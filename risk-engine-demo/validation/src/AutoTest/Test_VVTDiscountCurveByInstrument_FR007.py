"""
Valuation & Validation Tools python

@desc Boostrap test case: FR007 Swap Curve
@author (c) KPMG Advisory(China) LTD. FRM

"""

#  Copyright (C) Kpmg Advisory (China) Limited - All Rights Reserved
#  This source code is protected under international copyright law.  All rights
#  reserved and protected by the copyright holders.
#  This file is confidential and only available to authorized individuals with the
#  permission of the copyright holders.  If you encounter this file and do not have
#  permission, please contact the copyright holders and delete this file.
#  KPMG Advisory (China) FRM TGM 2024
#

from VVTpy.basics.VVTCalendar import CalendarTypes, BusDayAdjustTypes, VVTCalendar, DateGenRuleTypes
from VVTpy.basics.VVTCurrency import CurrencyTypes
from VVTpy.basics.VVTDate import VVTDate
from VVTpy.basics.VVTDayCount import DayCountTypes
from VVTpy.basics.VVTFrequency import FrequencyTypes
from VVTpy.basics.VVTGlobalTypes import SwapTypes, CompoundingTypes
from VVTpy.market.curves.VVTDiscountCurveByInstrument import VVTDiscountCurveByInstrument, ICSolverMethodType
from VVTpy.math.VVTInterpolator import InterpTypes
from VVTpy.instruments.mm.VVTIborDeposit import VVTIborDeposit
from VVTpy.instruments.ir.VVTIborSwap import VVTIborSwap


valuationDate = VVTDate(29, 12, 2023)
calendar = VVTCalendar(CalendarTypes.TARGET)
busDayAdjust = BusDayAdjustTypes.FOLLOWING
depoDCCType = DayCountTypes.ACT_365F
notional = 100.0
calendarType = CalendarTypes.TARGET
depos = []

# ON deposit
spotDays = 0
settlementDate = calendar.addBusinessDays(valuationDate, spotDays)
depositRate = 1.9149 / 100
maturityDate = calendar.addBusinessDays(settlementDate, 1)
depo = VVTIborDeposit(settlementDate, maturityDate, depositRate,
                      depoDCCType, notional, calendarType, busDayAdjustType=busDayAdjust)
depos.append(depo)

# 1 Week deposit
spotDays = 1
settlementDate = calendar.addBusinessDays(valuationDate, spotDays)
depositRate = 2.4 / 100
maturityDate = settlementDate.addTenor("1W")
depo = VVTIborDeposit(settlementDate, maturityDate, depositRate,
                      depoDCCType, notional, calendarType, busDayAdjustType=busDayAdjust)
depos.append(depo)

# 2 Week deposit
spotDays = 1
settlementDate = calendar.addBusinessDays(valuationDate, spotDays)
depositRate = 2.45 / 100
maturityDate = settlementDate.addTenor("2W")
depo = VVTIborDeposit(settlementDate, maturityDate, depositRate,
                      depoDCCType, notional, calendarType, busDayAdjustType=busDayAdjust)
depos.append(depo)


#IRS部分
spotDays = 1
startDate = calendar.addBusinessDays(valuationDate, spotDays)
swaps = []
fixedLegType = SwapTypes.PAY
fixedDCCType = DayCountTypes.ACT_365F
fixedFreqType = FrequencyTypes.QUARTERLY
floatFreqType = FrequencyTypes.QUARTERLY
notional = 100
principal = 0.0
floatSpread = 0.0
floatDCCType = DayCountTypes.ACT_365F
calendarType = CalendarTypes.TARGET
busDayAdjustRule = BusDayAdjustTypes.FOLLOWING
dateGenRuleTypes = DateGenRuleTypes.BACKWARD
currency = CurrencyTypes.CNY
couponCompoundingType = CompoundingTypes.SIMPLE
indexName = "FR007"
indexFreq = FrequencyTypes.WEEKLY
resetLag = -1    # GFv3
discCurve = None

# 1 month IRS
swapRate = 2.12 / 100
swap = VVTIborSwap(startDate, "1M", fixedLegType, swapRate,
                   fixedFreqType, fixedDCCType, notional,
                   floatSpread, floatFreqType, floatDCCType,
                   calendarType, busDayAdjustRule, dateGenRuleTypes, currency, couponCompoundingType,
                   indexName, indexFreq, resetLag)

swaps.append(swap)


# 3 month IRS
swapRate = 2.01 / 100
swap = VVTIborSwap(startDate, "3M", fixedLegType, swapRate,
                   fixedFreqType, fixedDCCType, notional,
                   floatSpread, floatFreqType, floatDCCType,
                   calendarType, busDayAdjustRule, dateGenRuleTypes, currency, couponCompoundingType,
                   indexName, indexFreq, resetLag)

swaps.append(swap)

# 6 month IRS
swapRate = 1.98875 / 100
swap = VVTIborSwap(startDate, "6M", fixedLegType, swapRate,
                   fixedFreqType, fixedDCCType, notional,
                   floatSpread, floatFreqType, floatDCCType,
                   calendarType, busDayAdjustRule, dateGenRuleTypes, currency, couponCompoundingType,
                   indexName, indexFreq, resetLag)

swaps.append(swap)

# 9 month IRS
swapRate = 1.985 / 100
swap = VVTIborSwap(startDate, "9M", fixedLegType, swapRate,
                   fixedFreqType, fixedDCCType, notional,
                   floatSpread, floatFreqType, floatDCCType,
                   calendarType, busDayAdjustRule, dateGenRuleTypes, currency, couponCompoundingType,
                   indexName, indexFreq, resetLag)

swaps.append(swap)

# 1 year IRS
swapRate = 1.99375 / 100

swap = VVTIborSwap(startDate, "1Y", fixedLegType, swapRate,
                   fixedFreqType, fixedDCCType, notional,
                   floatSpread, floatFreqType, floatDCCType,
                   calendarType, busDayAdjustRule, dateGenRuleTypes, currency, couponCompoundingType,
                   indexName, indexFreq, resetLag)

swaps.append(swap)

# 2 year IRS
swapRate = 2.025 / 100

swap = VVTIborSwap(startDate, "2Y", fixedLegType, swapRate,
                   fixedFreqType, fixedDCCType, notional,
                   floatSpread, floatFreqType, floatDCCType,
                   calendarType, busDayAdjustRule, dateGenRuleTypes, currency, couponCompoundingType,
                   indexName, indexFreq, resetLag)

swaps.append(swap)


# 曲线构建
liborCurve = VVTDiscountCurveByInstrument(valuationDate,
                                          depos,
                                          swaps,
                                          discCurve,
                                          freqType=FrequencyTypes.QUARTERLY,
                                          calendarType=calendarType,
                                          dayCountType=DayCountTypes.ACT_365F,
                                          compoundingType=CompoundingTypes.CONTINUOUS,
                                          busDayRuleType=busDayAdjust,
                                          interpType=InterpTypes.LINEAR_ZERO_RATES,
                                          checkRefit=True,
                                          solverMethod=ICSolverMethodType.Newton)

print("==================================Curve Details=========================================")
print(liborCurve)