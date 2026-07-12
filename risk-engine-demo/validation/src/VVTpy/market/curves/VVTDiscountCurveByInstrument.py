#  Copyright (C) Kpmg Advisory (China) Limited - All Rights Reserved
#  This source code is protected under international copyright law.  All rights
#  reserved and protected by the copyright holders.
#  This file is confidential and only available to authorized individuals with the
#  permission of the copyright holders.  If you encounter this file and do not have
#  permission, please contact the copyright holders and delete this file.
#  KPMG Advisory (China) FRM TGM 2024
#
#
#
#
#
#

from enum import Enum

import numpy as np
from scipy import optimize

from VVTpy.basics.VVTCalendar import CalendarTypes, BusDayAdjustTypes
from VVTpy.basics.VVTDate import VVTDate
from VVTpy.basics.VVTDayCount import DayCountTypes
from VVTpy.basics.VVTError import VVTError
from VVTpy.basics.VVTFrequency import FrequencyTypes
from VVTpy.basics.VVTGlobalTypes import CompoundingTypes
from VVTpy.basics.VVTGlobalVariables import gDaysInYear
from VVTpy.basics.VVTHelperFunctions import checkArgumentTypes, labelToString
from VVTpy.instruments.ir.VVTIborSwap import VVTIborSwap
from VVTpy.instruments.mm.VVTIborDeposit import VVTIborDeposit
from VVTpy.market.curves.VVTBaseDiscountCurve import VVTBaseDiscountCurve, DiscountCurveTypes
from VVTpy.math.VVTInterpolator import InterpTypes, VVTInterpolator
import better_exceptions
better_exceptions.hook()


###############################################################################
# Instrument Composed Curve求解方法类型
class ICSolverMethodType(Enum):
    Newton = 0,
    QuadraticMin = 1,


###############################################################################


deptol = 1e-5  # 拟合误差水平depo
swaptol = 1e-5  # 拟合误差水平swap


###############################################################################
def _fdeposit(df, *args):
    ''' 采用newton优化求解Deposit '''

    curve = args[0]
    depo = args[2]
    numPoints = len(curve._times)
    curve._dfs[numPoints - 1] = df

    curve._interpolator.fit(curve._times, curve._dfs)
    v_deposit = depo.value(depo._startDate, curve)
    notional = depo._notional
    v_deposit = v_deposit - notional

    return v_deposit


###############################################################################

def _f(df, *args):
    ''' 采用newton优化求解IRS '''

    curve = args[0]
    disCurve = args[3]
    if disCurve is None:  # GF 0529
        disCurve = args[0]  # GF 0529
    valueDate = args[1]
    swap = args[2]
    numPoints = len(curve._times)
    curve._dfs[numPoints - 1] = df

    curve._interpolator.fit(curve._times, curve._dfs)
    v_swap = swap.value(valueDate, disCurve, curve, None)
    v_swap /= swap._notional

    return v_swap


###############################################################################


class VVTDiscountCurveByInstrument(VVTBaseDiscountCurve):
    '''本类主要用于根据Ibor deposits以及 IRS利率构建即期收益率曲线。
    VVTDiscountCurveByInstrument是VVTDiscountCurve的子类，所以默认具有VVTDiscountCurve的所有功能。'''

    ###############################################################################

    def __init__(self,
                 valuationDate: VVTDate,  # Curve Date
                 iborDeposits: list,  # Depo
                 iborSwaps: list,  # IRS
                 dualCurve,  # IRS折现曲线
                 interpType: InterpTypes = InterpTypes.LINEAR_ZERO_RATES,  # 插值方法
                 freqType: FrequencyTypes = FrequencyTypes.QUARTERLY,  # 计息方式
                 dayCountType: DayCountTypes = DayCountTypes.ACT_365F,  # 日算惯例
                 calendarType: CalendarTypes = CalendarTypes.WEEKEND,  # 假日惯例
                 busDayRuleType: BusDayAdjustTypes = BusDayAdjustTypes.MODIFIED_FOLLOWING,  # 假日处理惯例
                 compoundingType: CompoundingTypes = CompoundingTypes.CONTINUOUS,  # 复利类型
                 checkRefit: bool = False,  # 检查Ibor curve是否正常拟合了输入的金融工具
                 solverMethod: ICSolverMethodType = ICSolverMethodType.Newton,  # solver方法
                 ):
        ''' 通过估值日期，三种金融工具列表和插值方法来初始化。
        三种金融工具可以部分为空，曲线默认t=0时，DF = 1.插值方式默认是FLAT_FWD_RATES。
        '''

        checkArgumentTypes(self.__init__, locals())

        self._valuationDate = valuationDate  # 曲线日期，估值日
        self._validateInputs(iborDeposits, iborSwaps)  # 对金融工具进行数据校验
        self._interpType = interpType  # 插值方式
        self._dayCountType = dayCountType
        self._freqType = freqType  # 付息频率
        self._calendarType = CalendarTypes(calendarType)
        self._busDayRuleType = BusDayAdjustTypes(busDayRuleType)
        self._curveType = DiscountCurveTypes.IC  # Instrument Composed曲线
        self._compoundingType = compoundingType  # 复利类型

        self._dualCurve = dualCurve  # IRS折现曲线

        self._checkRefit = checkRefit  # 拟合检查标识

        self._interpolator = None  # 插值器
        self._solverMethod = solverMethod  # 求解模型
        self._buildCurve()

    ###############################################################################

    def _buildCurve(self):
        ''' 初始化曲线结构. '''

        if self._solverMethod == ICSolverMethodType.QuadraticMin:
            self._buildCurveUsingQuadraticMinimiser()
        elif self._solverMethod == ICSolverMethodType.Newton:
            self._buildCurveUsing1DSolver()
        else:
            raise VVTError("无效求解器参数")

    ###############################################################################

    def _validateInputs(self,
                        iborDeposits,
                        iborSwaps):
        # 对两种金融工具进行数据校验

        numDepos = len(iborDeposits)
        numSwaps = len(iborSwaps)

        depoStartDate = self._valuationDate
        swapStartDate = self._valuationDate

        # 至少需要有1个用来校准的金融工具
        if numDepos + numSwaps == 0:
            raise VVTError("至少需要1个金融工具构建曲线")

        # 拆借利率，年化单利，数据质量检查
        if numDepos > 0:

            depoStartDate = iborDeposits[0]._startDate

            for depo in iborDeposits:
                # 类型检查
                if isinstance(depo, VVTIborDeposit) is False:
                    raise VVTError("短端必须使用Deposit作为基础金融工具")

                startDate = depo._startDate

                # deposit的最早开始日不能早于曲线日期
                if startDate < self._valuationDate:
                    raise VVTError("第一个Deposit的开始日不能早于曲线日期")

                # 兜底规则
                if startDate < depoStartDate:
                    depoStartDate = startDate

            for depo in iborDeposits:
                startDt = depo._startDate
                endDt = depo._maturityDate

                # deposit到期日不能小于等于开始日
                if startDt >= endDt:
                    raise VVTError("Deposit到期日不能小于等于开始日")

        # 检查deposit到期日保持递增
        if numDepos > 1:

            prevDt = iborDeposits[0]._maturityDate
            for depo in iborDeposits[1:]:
                nextDt = depo._maturityDate
                if nextDt <= prevDt:
                    raise VVTError("Deposit需要按顺序构建曲线")
                prevDt = nextDt

        # IRS利率，数据质量检查
        if numSwaps > 0:

            swapStartDate = iborSwaps[0]._effectiveDate

            for swap in iborSwaps:
                # 型检查
                if isinstance(swap, VVTIborSwap) is False:
                    raise VVTError("长端必须使用IRS作为基础金融工具")

                startDt = swap._effectiveDate
                # 开始日检查
                if startDt < self._valuationDate:
                    raise VVTError("第一个IRS的开始日不能早于曲线日期")

                # 兜底规则
                if swap._effectiveDate < swapStartDate:
                    swapStartDate = swap._effectiveDate

        if numSwaps > 1:

            # VVTIborSwap的开始日必须全部相同
            startDt = iborSwaps[0]._effectiveDate
            for swap in iborSwaps[1:]:
                nextStartDt = swap._effectiveDate
                if nextStartDt != startDt:
                    raise VVTError("IRS开始日必须全部相同")

            # VVTIborSwap的到期日递增检查
            prevDt = iborSwaps[0]._maturityDate
            for swap in iborSwaps[1:]:
                nextDt = swap._maturityDate
                if nextDt <= prevDt:
                    raise VVTError("IRS的到期日必须递增")
                prevDt = nextDt

            # VVTIborSwap的cashflow的期限结构需要相同
            longestSwap = iborSwaps[-1]

            longestSwapCpnDates = longestSwap._fixedLeg._paymentDates

            for swap in iborSwaps[0:-1]:
                swapCpnDates = swap._fixedLeg._paymentDates

                numFlows = len(swapCpnDates)
                # for iFlow in range(0, numFlows):
                #     if swapCpnDates[iFlow] != longestSwapCpnDates[iFlow]:
                #         raise VVTError("IRS的cashflow期限结构需要相同")

        # 检查三种金融工具之间的合理性
        lastDepositMaturityDate = VVTDate(1, 1, 1900)

        if numDepos > 0:
            lastDepositMaturityDate = iborDeposits[-1]._maturityDate

        if numSwaps > 0:
            firstSwapMaturityDate = iborSwaps[0]._maturityDate

        if numDepos > 0 and numSwaps > 0:
            if firstSwapMaturityDate <= lastDepositMaturityDate:
                raise VVTError("第一个IRS的开始日必须晚于最后一个Deposit的到期日")

        if swapStartDate > self._valuationDate:

            if numDepos == 0:
                raise VVTError("如果IRS的生效日不是曲线日期必须有至少一个Deposit")

            # 如果第一个Deposit的开始日晚于曲线日期，默认构建ON点采用第一个Deposit
            '''if depoStartDate > self._valuationDate:
                firstDepo = iborDeposits[0]
                if firstDepo._startDate > self._valuationDate:
                    syntheticDeposit = copy.deepcopy(firstDepo)
                    syntheticDeposit._startDate = self._valuationDate
                    syntheticDeposit._maturityDate = firstDepo._startDate
                    iborDeposits.insert(0, syntheticDeposit)
                    numDepos += 1'''

        self._usedDeposits = iborDeposits
        self._usedSwaps = iborSwaps

    ###############################################################################
    def _buildCurveUsing1DSolver(self):
        ''' 使用牛顿迭代求解df'''

        self._interpolator = VVTInterpolator(self._interpType)
        self._times = np.array([])
        self._dfs = np.array([])
        self._zeroRates = np.array([])
        self._zeroDates = np.array([])
        self._dfDates = np.array([])

        dfMat = 1.0
        self._times = np.append(self._times, 0.0)
        self._dfs = np.append(self._dfs, dfMat)
        self._interpolator.fit(self._times, self._dfs)
        self._zeroDates = np.append(self._zeroDates, self._valuationDate)

        # 先求第一个ON点(depo)

        for depo in self._usedDeposits:
            tmat = (depo._maturityDate - self._valuationDate) / gDaysInYear
            self._times = np.append(self._times, tmat)
            self._dfs = np.append(self._dfs, dfMat)
            self._zeroDates = np.append(self._zeroDates, depo._maturityDate)
            self._interpolator.fit(self._times, self._dfs)
            argtuple = (self, self._valuationDate, depo)

            dfMat = optimize.newton(_fdeposit, x0=dfMat, fprime=None, args=argtuple,
                                    tol=1e-12, maxiter=100, fprime2=None,
                                    full_output=False)

        dfMat = 1.0
        for swap in self._usedSwaps:
            maturityDate = swap._fixedLeg._paymentDates[-1]
            tmat = (maturityDate - self._valuationDate) / gDaysInYear

            self._times = np.append(self._times, tmat)
            self._dfs = np.append(self._dfs, dfMat)
            self._zeroDates = np.append(self._zeroDates, swap._maturityDate)
            argtuple = (self, self._valuationDate, swap, self._dualCurve)

            dfMat = optimize.newton(_f, x0=dfMat, fprime=None, args=argtuple,
                                    tol=1e-12, maxiter=100, fprime2=None,
                                    full_output=False)

        self._zeroRates = self.dfToZeroRate(self._zeroDates, self._freqType, self._dayCountType, self._compoundingType)
        self._dfDates = self._zeroDates

        if self._checkRefit is True:  # 结果拟合检查
            self._checkRefits(deptol, swaptol)

    ###############################################################################
    def _checkRefits(self, depoTol, swapTol):
        ''' 检查Ibor curve是否正常拟合了输入的金融工具 '''

        for depo in self._usedDeposits:
            # v = depo.value(self._valuationDate, self) / depo._notional
            v = depo.value(depo._startDate, self) / depo._notional
            if abs(v - 1.0) > depoTol:
                print("Value", v)
                print("abs(v - 1.0)", abs(v - 1.0))
                print("depoTol", depoTol)
                print(depo)
                raise VVTError("曲线构建结果针对Deposit检验失败。 Deposit: " +
                               depo._maturityDate.__repr__() + " 差异：" + str(abs(v - 1.0)))
            else:
                print("曲线构建结果针对Deposit检验成功。 Deposit: ", depo._maturityDate.__repr__())
                print("绝对差异", abs(v - 1.0))

        for swap in self._usedSwaps:
            if self._dualCurve is None:
                v = swap.value(swap._effectiveDate, self, self, None)
            else:
                v = swap.value(swap._effectiveDate, self._dualCurve, self, None)
            v = v / swap._notional

            if abs(v) > swapTol:
                print("Value", v)
                print("abs(v)", abs(v))
                print("swapTol", swapTol)
                swap.printFixedLegPV()
                swap.printFloatLegPV()
                print(swap)
                raise VVTError("曲线构建结果针对IRS检验失败. IRS: " +
                               swap._maturityDate.__repr__() + " 差异：" + str(abs(v)))
            else:
                print("曲线构建结果针对IRS检验成功。IRS: ", swap._maturityDate.__repr__())
                print("绝对差异: ", abs(v))

    ###############################################################################

    def __repr__(self):

        s = labelToString("曲线类型", type(self).__name__)
        s += labelToString("曲线日期", self._valuationDate)

        for depo in self._usedDeposits:
            s += "===================================================="
            s += labelToString("DEPOSIT", "")
            s += depo.__repr__()

        for swap in self._usedSwaps:
            s += "===================================================="
            s += labelToString("IRS", "")
            s += swap.__repr__()

        numPoints = len(self._times)

        s += labelToString("INTERP TYPE", self._interpType)
        s += labelToString("Solver Method", self._solverMethod)

        s += labelToString("GRID TIMES ", "   GRID Date ", "   GRID DFS       GRID ZeroRate\n")
        for i in range(0, numPoints):
            s += labelToString("% 10.12f" % self._times[i],
                               " % 12s" % self._dfDates[i],
                               "   % 12.12f" % self._dfs[i]
                               )
            s += "   " + str(self._zeroRates[i])
            s += "\n"
        return s

    ###############################################################################

    def _print(self):
        print(self)
