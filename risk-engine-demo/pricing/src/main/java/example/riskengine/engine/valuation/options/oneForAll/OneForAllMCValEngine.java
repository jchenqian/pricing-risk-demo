package example.riskengine.engine.valuation.options.oneForAll;

import example.riskengine.common.QL;
import example.riskengine.common.bases.cashflow.CashFlowsMiddleProcess;
import example.riskengine.common.bases.cashflow.Cashflows;
import example.riskengine.common.bases.cashflow.Leg;
import example.riskengine.common.bases.cashflow.SimpleCashFlow;
import example.riskengine.common.bases.daycounters.DayCounter;
import example.riskengine.common.bases.math.matrixutilities.CholeskyDecomposition;
import example.riskengine.common.bases.math.matrixutilities.Matrix;
import example.riskengine.common.bases.termstructures.surfaces.Surface;
import example.riskengine.common.bases.termstructures.yieldcurves.ZeroCurve;
import example.riskengine.common.bases.time.Calendar;
import example.riskengine.common.bases.time.*;
import example.riskengine.common.bases.tools.HistoricalVol;
import example.riskengine.common.enums.mds.PriceType;
import example.riskengine.common.enums.mds.SurfaceType;
import example.riskengine.common.enums.rds.IndicatorType;
import example.riskengine.common.enums.rds.scenario.MrChangeType;
import example.riskengine.common.enums.tds.*;
import example.riskengine.common.exceptions.EngineCodeEnums;
import example.riskengine.common.exceptions.EngineException;
import example.riskengine.data.mds.bo.price.Price;
import example.riskengine.data.rds.bo.GreekSensitivity;
import example.riskengine.data.tds.bo.exotic.OneForAll;
import example.riskengine.data.tds.bo.exotic.OptionPayoffs;
import example.riskengine.data.tds.bo.exotic.OptionTerms;
import example.riskengine.data.tds.bo.exotic.OptionUnderlyings;
import example.riskengine.engine.valuation.BaseOptionValEngine;
import org.apache.commons.math3.linear.RealMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author qianchen
 * @date 2023/6/12
 * @description 场外期权自定义模型计量引擎
 * @note 2024/4/9 新增累积判断逻辑
 */
@Component("EXOTIC_OPTION_BARRIER_VALMODEL_MONTE_CARLO_USERMODEL")
@Scope("prototype")
public class OneForAllMCValEngine extends BaseOptionValEngine {
    private static final Logger logger = LoggerFactory.getLogger(OneForAllMCValEngine.class);
    private final int path = 500000;
    private double change;
    private double epsilon = 10.0;
    private double bump_bp = 0.0001;
    private double rfr = 0.0;
    private double position;
    private double discountRate = 0.0;
    //    private final static Set<OptionTermType> allTerm = new HashSet<>(Arrays.asList(OptionTermType.KNOCK_IN, OptionTermType.KNOCK_OUT, OptionTermType.LIZARD));
    private List<OptionTerms.OptionTermElement> optionTermList = new ArrayList<>();
    private List<OptionPayoffs.OptionPayoffElement> optionPayoffList = new ArrayList<>();
    private List<OptionUnderlyings.OptionUnderlyingElement> optionUnderlyingList = new ArrayList<>();
    private List<OptionUnderlyings.OptionUnderlyingElement> originalOptionUnderlyingList = new ArrayList<>();
    private Map<OptionTermType, OneForAll.OptionObsByTerm> obsByTerm = new ConcurrentHashMap<>();
    private Map<OptionTermType, List<OptionPayoffs.OptionPayoffElement>> payoffsByTerm = new ConcurrentHashMap<>();
    private ArrayList<Matrix> dailySimuMatrixList = new ArrayList<>(); // 各个标的的daily矩阵
    private ArrayList<Matrix> pathStMatrixList = new ArrayList<>();
    private Matrix pathStMatrix = new Matrix(path, 1);
    private Matrix pathSpotMatrix = new Matrix(path, 1);
    private Matrix pathKnockOutIndexMatrix = new Matrix(path, 1);
    private Matrix pathKnockOutPriceMatrix = new Matrix(path, 1);
    private Matrix pathKnockOutMatrix = new Matrix(path, 1);
    private Matrix pathLizardMatrix = new Matrix(path, 1);
    private Matrix pathKnockInMatrix = new Matrix(path, 1);
    private Matrix pathProtectionMatrix = new Matrix(path, 1);
    private Matrix pathDividendMatrix;

    /**
     * path * tList size的矩阵, 存放每个观察日的累积数量
     */
    private Matrix pathAccumulateMatrix;

    /**
     * path * obsDate(累积期权结算日) size的矩阵, 存放每个结算日当天的基准价/收盘价
     */
    private Matrix valdatesStMatrix;

    /**
     * path * tList size的矩阵, 存放每个观察日的当天触发的事件
     */
    private Matrix eventMatrixByDay;
    private Matrix payoffMatrix = new Matrix(path, 1);
    private Matrix payoffMatrixwAccumulate = new Matrix();
    private ZeroCurve discountCurve;
    private QLDate maturityDate, startDate, endDate;
    private double maturityYearFrac, term, notional;
    private Boolean isBestPerformance = false;
    private Boolean isWorstPerformance = false;
    private Boolean isCallOption;
    private Double accrualPremium;
    private boolean isReVal = false;
    private Double pv_up = 0.0, pv_down = 0.0, pv = 0.0;
    private Leg dividends = new Leg();
    private Double histAccuUnits = 0.0; // 历史accuUnit情况
    private Double histAccuAmount = 0.0; // 历史结算金额
    private Double histAccuPaymentAmount= 0.0; // 历史accuUnit情况
    private Double unitNumbers = 0.0;
    private Double maxAccuUnits = Double.POSITIVE_INFINITY;
    private Boolean histKnockOutFlag = false;
    private Boolean histKnockOutLayerFlag = false;
    private Boolean histLizardFlag = false;
    private QLDate histKnockOutDate;
    private QLDate histKnockOutLayerDate;
    private int histKnockOutDatePosition;
    private int histKnockOutLayerDatePosition;
    private Leg accumulates = new Leg();
    private Matrix knockInMatrix;
    private Matrix knockInLayerMatrix;
    private Matrix knockOutMatirx;
    private Boolean ternimateFlag, returnFlag;
    private Boolean isBuy;
    private Boolean isContainAccumulate;
    //    private DayCounter dayCounter;
    private Calendar calendar;
    private Frequency settlementFrequency, paymentMultiplierFrequency;
    private int knockOutValDatePosition;
    private int totalBusinessDays;
    private Double target, fixedPay;
    private ExerciseType optionType;
    private AverageRule averageRule;
    private Double enhancedPrice;
    private Double delta=0.0, gamma=0.0, vega=0.0, rho=0.0, theta=0.0;


    @Override
    protected void prepare() {
        OneForAll exotic = (OneForAll) this.instrument;
        qlValDate = new QLDate(this.cacheCfg.getValDate());
        dayCounter = exotic.getDayCounter();

        // 初始化期权信息
        this.optionUnderlyingList = exotic.getOptionUnderlyingList();
        this.optionTermList.addAll(exotic.getOptionTermList());
        this.isContainAccumulate = exotic.getAccuOption();
        this.optionPayoffList = exotic.getOptionPayoffList();
        this.payoffsByTerm = exotic.getPayoffsByTerm();
        this.optionType = exotic.getExerciseType();// 针对亚式期权特殊处理
        this.averageRule = exotic.getAverageRule();
        this.enhancedPrice = exotic.getEnhancedPrice();
        this.position = exotic.getPriorPosition();

        // 初始化累积期权信息
        this.unitNumbers = exotic.getUnitNumber();
        this.maxAccuUnits = exotic.getMaxAccuUnits();
        this.pathProtectionMatrix.fill(0.0);
        this.settlementFrequency = exotic.getSettlementFrequency();
        this.paymentMultiplierFrequency = exotic.getPaymentMultiplierFrequency();
        this.startDate = exotic.getStartDate();
        this.endDate = exotic.getEndDate();
        initAccumulateTerm();

        // 初始化期权交易信息
        this.calendar = rds.getCalendars().getCalendar(exotic.getCalendar());
        this.maturityDate = calendar.adjust(exotic.getMaturityDate(), BusinessDayConvention.Following);
        this.maturityYearFrac = dayCounter.yearFraction(qlValDate, maturityDate);
        this.term = this.dayCounter.yearFraction(startDate, endDate);
        this.isCallOption = exotic.getCall();
        this.isBuy = exotic.getBuy();
        this.notional = exotic.getNotional();
        this.accrualPremium = exotic.getAccrualPremium();
        this.ternimateFlag = exotic.getTerminateFlag();
        this.returnFlag = exotic.getReturnFlag();
        this.isBestPerformance = "BEST".equals(exotic.getMultiType());
        this.isWorstPerformance = "WORST".equals(exotic.getMultiType());
        this.discountCurve = super.mds.getIrCurve(exotic.getDiscountCurve()).getOneDateCurve(qlValDate);


        // 初始化标的基本信息
        for (int i = 0; i < this.optionUnderlyingList.size(); i++) {
            OptionUnderlyings.OptionUnderlyingElement oneUnderlying = optionUnderlyingList.get(i);
            PriceType underlyingType = oneUnderlying.getType();
            String underlyingCode = oneUnderlying.getUnderlyingCode();
            Price underlyingPrice = super.mds.getTypePrices(underlyingType, underlyingCode).clonePrice();

            // 根据标的初始化波动率
            double volatility = 0, r = 0, dividend = 0, drift = 0.0;
            volatility = calculateOptionVolatility(oneUnderlying);

            // 初始化模拟使用的rfr
            if (oneUnderlying.getRfr() != null) {
                r = oneUnderlying.getRfr();
                this.discountRate = r;
            } else {
                ZeroCurve simuCurve = super.mds.getIrCurve(oneUnderlying.getSimuCurveName()).getOneDateCurve(qlValDate);
                double dfTerm = simuCurve.dayCounter().yearFraction(qlValDate, this.maturityDate);
                r = simuCurve.getZeroYield(dfTerm);
            }

            // 初始化股息率(蒙卡模拟用)
            try {
                if (oneUnderlying.getDividend() != null) {
                    dividend = oneUnderlying.getDividend();
                } else if (underlyingType.equals(PriceType.STOCK) || underlyingType.equals(PriceType.STOCK_INDEX)) {
                    dividend = super.mds.getTypePriceByDay(oneUnderlying.getDividendType(), underlyingCode, qlValDate);
                } else {
                    dividend = r;
                    logger.warn("交易号:"+this.instrument.getInstId()+"未找到标的资产分红派息数据, drift(漂移项)使用0.0计算! ! !");
                }
            } catch (Exception e) {
                logger.warn("交易号:"+this.instrument.getInstId()+"未找到标的资产分红派息数据, dividend使用0.0计算! ! !");
                dividend = 0.0;
            }
            drift = r - dividend;

            double S0 = underlyingPrice.getPriceByDate(qlValDate);
            oneUnderlying.setDrift(drift);
            oneUnderlying.setVolatility(volatility);
            oneUnderlying.setDividend(dividend);
            oneUnderlying.setUnderlyingPrice(underlyingPrice);
            oneUnderlying.setRfr(r);
            optionUnderlyingList.set(i, oneUnderlying);
            logger.debug("PV计算准备: 场外期权交易编号: {}, 标的代码: {}, S0: {}, 波动率: {}, 股息率: {}, 插值无风险利率: {}, quantoAdjust: {}",
                    instrument.getInstId(), underlyingCode, S0, volatility, dividend, r, 0.0);
        }

        // 初始化条款观察日, 准备计算
        this.obsByTerm = exotic.getObsByTerm();
        for (int i = 0; i < this.optionTermList.size(); i++) {
            OptionTerms.OptionTermElement term = optionTermList.get(i);
            OptionTermType current = term.getTerm();

            if (!obsByTerm.containsKey(current)) {
                List<QLDate> dateList = new ArrayList<>();
                List<Double> tList = new ArrayList<>();
                List<Double> levelList = new ArrayList<>();
                List<Double> rebateRateList = new ArrayList<>();

                Frequency obsFreq = term.getObsFrequency();
                QLDate startDate = term.getStartDate();
                QLDate endDate = term.getEndDate();

                if (startDate.eq(endDate)) {
                    dateList.add(startDate);
                    levelList.add(term.getLevel());
                    tList.add(this.dayCounter.yearFraction(startDate, endDate));
                } else {
                    Schedule schedule = new Schedule(startDate, endDate, new Period(obsFreq), rds.getCalendars().getCalendar(this.optionUnderlyingList.get(0).getCalendarName()),
                            BusinessDayConvention.Following, BusinessDayConvention.Following, DateGeneration.Rule.Forward, false);

                    for (int j = 0; j < schedule.dates().size(); j++) {
                        if (obsFreq.equals(Frequency.Once) && j == 0) {
                            continue;
                        }
                        dateList.add(schedule.dates().get(j));
                        levelList.add(term.getLevel()); // 未录入观察日列表默认全部水平为条款执行水平
                        rebateRateList.add(current.equals(OptionTermType.ACCUMULATE) ? 0.0 : this.payoffsByTerm.get(current).get(0).getRate1());  // 在计算敲出payoff时需初始化obs中的rebateRate, 其他条款使用该条款对应payoff中第一条的rate1
                    }
                    for (int j = 0; j < schedule.dates().size(); j++) {
                        double dt = 0.0;
                        double count = schedule.date(j).sub(qlValDate);
                        if (count <= 0) {
                            dt = 0.0;
                        } else {
//                            dt = 1.0 / 243.0;
                            dt = this.dayCounter.yearFraction(startDate, schedule.date(j));
                        }
                        tList.add(dt);
                    }
                }

                OneForAll.OptionObsByTerm obs = new OneForAll.OptionObsByTerm();
                obs.setObsDates(dateList);
                obs.setObsLevel(levelList);
                obs.setObsTerm(tList);
                obs.setRebateRates(rebateRateList);

                this.obsByTerm.put(current, obs);
            }
        }
        randnMatrixs = new ArrayList<>(optionUnderlyingList.size());
//        mcSimulateAllPath();

        // 重估值标志
        if (0.0 == pv)
            // 计算单份的PV
            pv = calculatePV();

        this.isReVal = true;

    }

    @Override
    protected Double calc(IndicatorType indicatorType) {
        Double result = null;
        switch (indicatorType) {
            case PV:
                result = position * pv;
                logger.debug("交易号:"+this.instrument.getInstId()+"自定义期权{}: {}次模拟路径估值结果: {}", this.instrument.getInstId(), path, result);
                break;
            case DELTA:
                result = calculateDelta();
                break;
            case DELTA_AMOUNT:
                result = calculateDeltaAmount(false);
                break;
            case DELTA_ADJUSTED:
                result = calculateDeltaAmount(true);
                break;
            case GAMMA:
                result = calculateGamma();
                break;
            case GAMMA_AMOUNT:
                result = calculateGammaAmount(false);
                break;
            case GAMMA_ADJUSTED:
                result = calculateGammaAmount(true);
                break;
            case VEGA:
                result = calculateVega();
                break;
            case VEGA_AMOUNT:
                result = calculateVega() * this.position;
                break;
            case THETA:
                result = calculateTheta();
                break;
            case THETA_AMOUNT:
                result = calculateTheta() * this.term * this.position;
                break;
            case RHO:
                result = calculateRho();
                break;
            case ACCRUED_PREMIUM:
                result = this.accrualPremium;
                break;
            case HIS_VOL:
                result = calculateVolatilityForVegaCapital();
                break;
            default:
                break;
        }
        return result;
    }

    @Override
    public void clear(){
        optionTermList.clear();
        originalOptionUnderlyingList.clear();
        dailySimuMatrixList.clear();
        pathStMatrixList.clear();
        pathStMatrix = null;
        pathSpotMatrix = null;
        pathKnockOutIndexMatrix = null;
        pathKnockOutPriceMatrix = null;
        pathKnockOutMatrix = null;
        pathLizardMatrix = null;
        pathKnockInMatrix = null;
        pathProtectionMatrix = null;
        pathDividendMatrix = null;
        pathAccumulateMatrix = null;
        valdatesStMatrix = null;
        eventMatrixByDay = null;
        payoffMatrix  = null;
        payoffMatrixwAccumulate  = null;
        optionTermList = null;
        optionUnderlyingList = null;
        originalOptionUnderlyingList = null;
        obsByTerm = null;
        payoffsByTerm = null;
        dailySimuMatrixList = null;
        pathStMatrixList = null;
        randoms = null;
        knockInMatrix = null;
        knockOutMatirx = null;
    }

    /**
     * @return 最终估值结果
     * @author qianchen
     * @date 2023/6/26
     * @description 蒙卡矩阵方法计算PV
     */
    private Double calculatePV() {
        mcSimulateAllPath();
        double result = 0.0, sum = 0.0;
        if (isContainAccumulate) {
            sum = calculatePayoffwAccumulation();
        } else {
            this.payoffMatrix = calculatePayoff();
            sum = this.payoffMatrix.sum() / this.payoffMatrix.size();
        }
        result = isBuy ? sum : -sum;
        return result;
    }


    /**
     * @return Payoff矩阵
     * @author qianchen
     * @date 2023/6/26
     * @description 计算所有路径payoff
     */
    private Matrix calculatePayoff() {
        // 初始化payoff信息
        Matrix result = new Matrix(path, 1);
        for (int addr = 0; addr < result.rows(); addr++) {
            // 计算条款收益
            List<OptionPayoffs.OptionPayoffElement> currentPayoff = calculatePayoffByTerm(addr);
            double res = 0.0;
            double dividendYield = 0.0;
            double discountFactor = 0.0;
            double rfr = 0.0;
            for (OptionPayoffs.OptionPayoffElement temp : currentPayoff) {
                double rate = temp.calculateRate();
                double term = this.dayCounter.yearFraction(qlValDate, temp.getMaturityDate());
                if (this.discountRate == 0.0) {
                    rfr = this.discountCurve.getZeroYield(term);
                    this.rfr = rfr;
                } else {
                    rfr = this.discountRate;
                }
                discountFactor = Math.exp(-rfr * term);
                double amount = rate;
                res += amount * discountFactor * (temp.getPlus() ? 1.0 : -1.0);
            }
            // 计算派息收益
            if (this.obsByTerm.containsKey(OptionTermType.DIVIDEND)) {
                dividendYield = Cashflows.npv(this.dividends, this.discountCurve, true, this.qlValDate, Compounding.Continuous, Frequency.Once);
                this.dividends.clear();
            }

            // 不区分买入卖出方向, 引擎输出期权绝对价值
            result.$[addr] = res + dividendYield;
        }
        return result;
    }


    private Double calculatePayoffwAccumulation() {
        Matrix accuNumbers = new Matrix(path, this.valdatesStMatrix.cols());

        /**
         * step 00: Once结算 && 历史已经敲出, 后续不再观察和计算金额和折现因子
         */
        if (histKnockOutFlag && !returnFlag && this.settlementFrequency.equals(Frequency.Once)) {
            Matrix paymentMatrix = new Matrix(path, 1);
            paymentMatrix.fill(this.histAccuPaymentAmount);

            List<QLDate> paymentDates = new ArrayList<>();
            paymentDates.add(this.maturityDate);
            Matrix dfMatrix = paymentMatrix.discountFactor(qlValDate, discountCurve, paymentDates, dayCounter, this.discountRate);

            Matrix result = paymentMatrix.calculatePV(paymentMatrix, dfMatrix);
            return result.sum();

        } else {
            /**
             * step 01: 获取逐日累积矩阵, 加工每个结算日的结算金额  &&  step 02: 计算结算日金额, 调整Protection payoff
             */
            if (this.settlementFrequency.equals(Frequency.Once)) {
                accuNumbers.calcAccuAmountByFreq(pathAccumulateMatrix, pathKnockOutIndexMatrix, Frequency.Once, returnFlag, knockOutValDatePosition, this.histAccuUnits);
            } else if (this.settlementFrequency.equals(Frequency.Daily)) {
                accuNumbers.calcAccuAmountByFreq(pathAccumulateMatrix, pathKnockOutIndexMatrix, Frequency.Daily, returnFlag, knockOutValDatePosition, 0.0);
            } else {
                // TODO 获取离散的定期观察日，加工数量到指定日期
            }

            /**
             * step 02: 计算结算日金额, 调整Protection payoff, 叠加Protection金额
             */
            Matrix paymentMatrix = new Matrix();
            Matrix protectionMatrix = new Matrix(path, 1);
            protectionMatrix.fill(0.0);
            double strike = this.optionUnderlyingList.get(0).getSpotPrice() * this.optionTermList.get(optionTermList.size()-1).getLevel();
            if (this.payoffsByTerm.containsKey(OptionTermType.PROTECTION)) {
                OptionPayoffs.OptionPayoffElement protectionPayoff = this.payoffsByTerm.get(OptionTermType.PROTECTION).get(0);
                protectionMatrix.calcProtPayment(qlValDate, this.pathProtectionMatrix, this.pathKnockOutPriceMatrix, this.pathKnockOutIndexMatrix,
                        this.obsByTerm.get(OptionTermType.KNOCK_OUT).getObsDates(), discountCurve, protectionPayoff, strike, this.unitNumbers, this.totalBusinessDays, this.discountRate, dayCounter);
                paymentMatrix = accuNumbers.calcAccuPayment(this.valdatesStMatrix, this.pathKnockOutPriceMatrix, this.pathKnockOutIndexMatrix, this.returnFlag, strike, this.settlementFrequency);
            } else {
                paymentMatrix = accuNumbers.calcAccuPayment(this.valdatesStMatrix, this.pathKnockOutPriceMatrix, this.pathKnockOutIndexMatrix, this.returnFlag, strike, this.eventMatrixByDay, this.fixedPay, this.target, this.settlementFrequency);
            }

            /**
             * step 03: 考虑是否有到期翻倍
             */
            double multiplier = 0.0;
            if (this.settlementFrequency.equals(Frequency.Daily)) {
                try {
                    List<OptionPayoffs.OptionPayoffElement> KnockINPayoffs = payoffsByTerm.get(OptionTermType.KNOCK_IN);
                    for (OptionPayoffs.OptionPayoffElement onePayoff: KnockINPayoffs) {
                        if (onePayoff.getType().equals(PayoffType.LAST_EXTRA)) {
                            multiplier = onePayoff.getAccuCoef();
                            break;
                        }
                    }
                } catch (EngineException e) {
                    multiplier = 0.0;
                }
                paymentMatrix.adjustMultiplier(this.pathKnockOutMatrix, this.pathKnockInMatrix, this.paymentMultiplierFrequency, multiplier, this.totalBusinessDays);
            } else if (this.settlementFrequency.equals(Frequency.Once)) {
                multiplier = 1.0;
                List<OptionPayoffs.OptionPayoffElement> KnockINPayoffs = payoffsByTerm.get(OptionTermType.KNOCK_IN);
                for (OptionPayoffs.OptionPayoffElement onePayoff: KnockINPayoffs) {
                    if (onePayoff.getType().equals(PayoffType.LAST)) {
                        multiplier = onePayoff.getAccuCoef();
                        break;
                    }
                }
                paymentMatrix.adjustMultiplier(this.pathKnockOutMatrix, this.pathKnockInMatrix, multiplier);
            }


            /**
             * step 04: 计算定期估值日的折现因子
             */
            List<QLDate> settlementDates = this.obsByTerm.get(OptionTermType.ACCUMULATE).getObsDates();
            List<QLDate> paymentDates = new ArrayList<>();
            Matrix dfMatrix = new Matrix();
            if (this.settlementFrequency.equals(Frequency.Once) && payoffsByTerm.containsKey(OptionTermType.KNOCK_OUT)) {
                /**
                 * 对于Once结算的, PaH, dfMatrix只有一列
                 */
                dfMatrix = paymentMatrix.discountFactor(qlValDate, discountCurve, obsByTerm.get(OptionTermType.KNOCK_OUT).getObsDates(), pathKnockOutIndexMatrix, dayCounter, discountRate);
            } else if (this.settlementFrequency.equals(Frequency.Daily)) {
                for (QLDate date : settlementDates) {
                    if (date.le(qlValDate))
                        continue;
                    paymentDates.add(date);
                }
                dfMatrix = paymentMatrix.discountFactor(qlValDate, discountCurve, paymentDates, dayCounter, this.discountRate);
            }

            /**
             * step 05: 计算payoff
             */
            Matrix result = paymentMatrix.calculatePV(paymentMatrix, dfMatrix);
            double normalPayment = result.sum();
            double protection = protectionMatrix.sum();
            double paymentAmount = (normalPayment + protection) / this.payoffMatrix.size();
            logger.debug("历史结算金额: {}, 模拟累计结算金额PV: {}", this.histAccuAmount, paymentAmount);
            return ( paymentAmount + this.histAccuAmount ) / this.position;
        }
    }

    /**
     * @author qianchen
     * @date 2023/6/19
     * @description 根据当前交易的条款触发情况和Payoff计算当前path的收益率
     */
    private List<OptionPayoffs.OptionPayoffElement> calculatePayoffByTerm(int index) {
        // 判断当前path上所有条款触发情况
        for (int i = 0; i < optionTermList.size(); i++) {
            OptionTerms.OptionTermElement current = optionTermList.get(i);
            OptionTermType type = current.getTerm();
            boolean currentPathFlag = getTermFlag(type, index);
            current.setTrue(currentPathFlag);
        }
        // 根据触发情况计算收益率
        OptionTerms terms = new OptionTerms();
        OptionTermType target = terms.getFirstByPath(optionTermList);
        // 初始化payoff
        initPayoffElements(index, target);
        initDividendYieldElenemts(index, this.obsByTerm.containsKey(OptionTermType.DIVIDEND));
//        initAccumulateElenemts(index, this.obsByTerm.containsKey(OptionTermType.ACCUMULATE), target);
        return this.payoffsByTerm.get(target);
    }

    /**
     * @author qianchen
     * @date 2023/10/9
     * @description 根据当前路径情况给PayoffElements中参与计算的变量赋值
     */
    private void initAccumulateElenemts(int addr, Boolean isContainsAccumulate, OptionTermType target) {
        if (isContainsAccumulate) {

            // 调整定期估值日
            List<QLDate> originalAccumulateObs = this.obsByTerm.get(OptionTermType.ACCUMULATE).getObsDates();
            List<QLDate> accumulateObs = new ArrayList<>();
            for (int i = 0; i < originalAccumulateObs.size(); i++) {
                if (originalAccumulateObs.get(i).gt(this.qlValDate)) {
                    accumulateObs.add(originalAccumulateObs.get(i));
                }
            }

            // 获取当前path的到期日
            List<OptionPayoffs.OptionPayoffElement> optionPayoffList = this.payoffsByTerm.get(target);
            QLDate maturityDate = optionPayoffList.get(0).getMaturityDate();

            // 当存在累积条款时, ACCUMULATE为termList中的最后一个
            Schedule accumulateSchedule = new Schedule(this.qlValDate, this.optionTermList.get(optionTermList.size() - 1).getEndDate(), new Period(Frequency.Daily), rds.getCalendars().getCalendar(this.optionUnderlyingList.get(0).getCalendarName()),
                    BusinessDayConvention.Following, BusinessDayConvention.Following, DateGeneration.Rule.Forward, false);
            int j = 1; //跳过估值日, 估值日的已经在历史中考虑
            LOOP:
            for (int i = 0; i < accumulateObs.size(); i++) {  // 根据定期估值日循环
                double amount = 0.0;
                double unit = 0.0;
                QLDate currentValDay = accumulateObs.get(i);

                for (; j < accumulateSchedule.size(); j++) { // TODO 找到区间的最大值和最小值, 直接加位置即可
                    QLDate currentAccumulateDate = accumulateSchedule.dates().get(j);
                    if (currentAccumulateDate.lt(currentValDay) && currentAccumulateDate.le(maturityDate)) {
                        unit += pathAccumulateMatrix.get(addr, j - 1);
                    } else if (currentAccumulateDate.eq(currentValDay) && currentAccumulateDate.le(maturityDate)) {
                        unit += pathAccumulateMatrix.get(addr, j - 1);
                        j++;
                        break;
                    } else if (currentAccumulateDate.gt(maturityDate)) {
                        break LOOP;
                    }

                }

                // 判断是否触发保股
                double protectionUnits = 0.0;
                if (this.pathProtectionMatrix.get(addr, 0) == 1) {
                    List<OptionPayoffs.OptionPayoffElement> current = this.payoffsByTerm.get(OptionTermType.PROTECTION);
                    double enhancedUnits = 0.0;
                    for (OptionPayoffs.OptionPayoffElement temp : current) {
                        temp.setAmount(this.unitNumbers);
                        double result = temp.calculateRate();
                        enhancedUnits += result;
                    }
                    double days = this.pathProtectionMatrix.get(addr, 0);
                    protectionUnits = enhancedUnits * days;
                }

                // 获取strike
                double spot = this.pathSpotMatrix.get(addr, 0);
                double strikeCoef = this.optionTermList.get(optionTermList.size() - 1).getLevel();
                double strike = spot * strikeCoef;

                // 计算最终累积数量和结算金额
                if (i == 0) {
                    unit += this.histAccuUnits;
                }
                unit = unit > this.maxAccuUnits ? this.maxAccuUnits : unit + protectionUnits;
                double St = this.valdatesStMatrix.get(addr, i);
                amount = this.isCallOption ? unit * (St - strike) : unit * (strike - St); // 计算该定期估值日的结算金额
                SimpleCashFlow oneValDay = new SimpleCashFlow(amount, currentValDay);
                this.accumulates.add(oneValDay);

                // 当前路径触发保股, 立即终止, 不再根据定期估值日生成现金流
                if (this.pathProtectionMatrix.get(addr, 0) == 1 && this.ternimateFlag)
                    break;
            }
        }
    }

    /**
     * @param addr 当前path
     * @author qianchen
     * @date 2023/6/20
     * @description 根据当前路径情况给PayoffElements所有变量赋值
     */
    private void initPayoffElements(int addr, OptionTermType target) {
//        for (OptionPayoffs.OptionPayoffElement temp : this.optionPayoffList) {
        List<OptionPayoffs.OptionPayoffElement> optionPayoffList = this.payoffsByTerm.get(target);
        for (OptionPayoffs.OptionPayoffElement temp : optionPayoffList) {
            double spot = this.pathSpotMatrix.get(addr, 0);
            temp.setSpot(spot);
            temp.setSt(this.pathStMatrix.get(addr, 0));
            temp.setStrike(temp.getStrikeCoef() * spot);
            OptionTermType term = temp.getTerm();
            // 默认KnockOut PaH 特殊处理
            if (term.equals(OptionTermType.KNOCK_OUT) && this.ternimateFlag) {
                // 此处已经判断触发条款为KnockOut, 所以不再判断position是不是-1
                int position = (int) pathKnockOutIndexMatrix.get(addr, 0);
                QLDate knockOutDate = this.obsByTerm.get(OptionTermType.KNOCK_OUT).getObsDates().get(position);
                double time = this.dayCounter.yearFraction(this.startDate, knockOutDate);
                temp.setTime(time);
                temp.setMaturityDate(knockOutDate);
                if (temp.getType().equals(PayoffType.FIXEDRATE) || temp.getType().equals(PayoffType.FIXED_RATE)) {  // 只有FIXEDRATE调整可变敲出收益率
                    temp.setRate1(this.obsByTerm.get(OptionTermType.KNOCK_OUT).getRebateRates().get(position));
                }
            } else {
                temp.setTime(this.term);  // PaE
                temp.setMaturityDate(this.maturityDate);
            }
        }
    }

    /**
     * @author qianchen
     * @date 2023/8/11
     * @description 根据当前路径情况给PayoffElements中派息分红赋值
     * @note 与其他条款不同, 派息在initPayoff里判断是否触发
     */
    private void initDividendYieldElenemts(int addr, Boolean isContainsDividend) {
        if (isContainsDividend) {
            dividends.clear();
            List<OptionPayoffs.OptionPayoffElement> optionPayoffList = this.payoffsByTerm.get(OptionTermType.DIVIDEND);
            for (OptionPayoffs.OptionPayoffElement temp : optionPayoffList) {
                temp.setParticipateRate(1.0);
                temp.setTime(this.term);
                for (int i = 0; i < this.pathDividendMatrix.cols(); i++) {
                    if (this.pathDividendMatrix.get(addr, i) == 1) {//触发派息
                        int dividendValDatePosition = calculateValDatePosition(this.obsByTerm.get(OptionTermType.DIVIDEND).getObsDates());
                        QLDate cashFlowDate = obsByTerm.get(OptionTermType.DIVIDEND).getObsDates().get(dividendValDatePosition + i);
                        QLDate maturity = new QLDate();
                        int knockOutIndex = (int) pathKnockOutIndexMatrix.get(addr, 0);
                        if (this.ternimateFlag && knockOutIndex != -1) {
                            // 可提前终止, 并且敲出
                            maturity = obsByTerm.get(OptionTermType.KNOCK_OUT).getObsDates().get(knockOutIndex);
                        } else {
                            maturity = this.maturityDate;
                        }
                        if (pathKnockOutMatrix.get(addr, 0) == 0 || maturity.ge(cashFlowDate)) {
                            dividends.add(new SimpleCashFlow(temp.calculateRate() * notional, cashFlowDate));//不考虑年化的分红派息收益
                        }
                    }
                }
            }
        }
    }

    /**
     * @param type  条款类型
     * @param index 当前path
     * @author qianchen
     * @date 2023/6/26
     * @description 获取当前路径条款触发状态
     */
    private boolean getTermFlag(OptionTermType type, int index) {
        double result = 0.0;
        switch (type) {
            case KNOCK_OUT:
                result = this.pathKnockOutMatrix.get(index, 0);
                break;
            case KNOCK_IN:
                result = this.pathKnockInMatrix.get(index, 0);
                break;
            case DIVIDEND:
                result = this.pathDividendMatrix.rangeRow(index).accumulate();
                break;
            case LIZARD:
                result = getLizardReuslt(index);
                break;
            default:
                break;
        }
        return result > 0.0;
    }


    /**
     * @author qianchen
     * @date 2023/6/26
     * @description 蒙卡路径模拟
     */
    private void mcSimulateAllPath() {
        this.dailySimuMatrixList.clear();
        /**
         * Step 1 生成所有标的的价格模拟矩阵并储存, Daily
         */
        for (int j = 0; j < optionUnderlyingList.size(); j++) {
            OptionUnderlyings.OptionUnderlyingElement oneUnderlying = optionUnderlyingList.get(j);
            String calendarName = oneUnderlying.getCalendarName();
            Schedule simuSchedule = new Schedule(this.startDate, this.endDate, new Period(Frequency.Daily), rds.getCalendars().getCalendar(calendarName),
                    BusinessDayConvention.Following, BusinessDayConvention.Following, DateGeneration.Rule.Forward, false);
            this.optionUnderlyingList.get(j).setSimuSchedule(simuSchedule); // daily
            if (isReVal) {//第i个标的已经生成的随机数矩阵
                randoms = randnMatrixs.get(j);
            }
            Matrix dailySimuMatrix;
            if (this.optionUnderlyingList.size() > 1 && j == 0) {
                double[][] prices = prepareForPearsonsCorrelation(this.qlValDate, this.optionUnderlyingList);
                RealMatrix correlationMatrix = GenerateCorrelationMatrix(prices);
                Matrix convert = new Matrix();
                CholeskyDecomposition cholesky = new CholeskyDecomposition(convert.convertRealMatrixToMatrix(correlationMatrix));
                this.dailySimuMatrixList.clear();
                this.dailySimuMatrixList = GenerateMultiMonteCarloMatrixs(this.optionUnderlyingList, simuSchedule, path, cholesky.L());
            } else if (this.optionUnderlyingList.size() == 1) {
                dailySimuMatrix = GenerateMonteCarloMatrix(oneUnderlying, simuSchedule, path, isReVal); // Daily
                this.dailySimuMatrixList.add(dailySimuMatrix);
            }
            if (!isReVal) {//第一次调用估值的时候才会真正生成随机数, 保存结果
                CashFlowsMiddleProcess oneUnderlyingMiddleProcess = new CashFlowsMiddleProcess()
                        .withValuationDate(this.qlValDate)
                        .withCashflowDate(this.qlValDate)
                        .withAmount(oneUnderlying.getVolatility())
                        .withPresentValue(oneUnderlying.getDividend())
                        .withRate(oneUnderlying.getRfr())
//                        .withDiscountFactor(oneUnderlying.getQuantoAdj())
                        .withDiscountFactor(0.0)
                        .withCalculationRate(oneUnderlying.getDrift())
                        .withCashflowtype(CashflowType.OPTION)
                        .withDiscountCurve(oneUnderlying.getUnderlyingCode());
                this.middleProcesses.add(oneUnderlyingMiddleProcess);

//                if (j == optionUnderlyingList.size() - 1)
//                    isReVal = true;//第一次随机数生成后改变重估值标识
            }
        }

        for (int i = 0; i < optionTermList.size(); i++) {
            List<Matrix> simuMatrixListByTerm = new ArrayList<>();
            OptionTerms.OptionTermElement optionTermElement = optionTermList.get(i);
            OptionTermType currentTerm = optionTermElement.getTerm();
            OneForAll.OptionObsByTerm currentObs = new OneForAll.OptionObsByTerm();
            if (optionTermElement.getTerm().equals(OptionTermType.ACCUMULATE)) {
                /**
                 * TODO ACCUMULATE的观察日, 用于模拟矩阵的生成, 为Daily观察, 与定期结算日区分
                 * TODO 定期估值日可离散可连续, 按照累积结算的频率生成, 存放于this.obsByTerm.get(OptionTermType.ACCUMULATE)
                 */
                List<QLDate> accumulateObs = new Schedule(optionTermElement.getStartDate(), optionTermElement.getEndDate(), new Period(Frequency.Daily), rds.getCalendars().getCalendar("CNY AS"),
                        BusinessDayConvention.Following, BusinessDayConvention.Following, DateGeneration.Rule.Forward, false).dates();
                currentObs.setObsDates(accumulateObs);
            } else {
                currentObs = this.obsByTerm.get(currentTerm);
            }

            /**
             * Step 2 根据条款观察日提取价格模拟矩阵
             */
            if (!optionTermElement.getTerm().equals(OptionTermType.PROTECTION) && !this.optionType.equals(ExerciseType.Asian)) {
                for (int j = 0; j < optionUnderlyingList.size(); j++) {
                    Matrix simuMatrix = extractSimuMatrix(currentObs.getObsDates(), optionUnderlyingList.get(0).getSimuSchedule(), this.dailySimuMatrixList.get(j), true);
                    Matrix pathStockPriceMatrix = new Matrix(path, 1); // 不包含估值日数据
                    pathStockPriceMatrix.fillCol(0, simuMatrix, simuMatrix.cols() - 1);
                    simuMatrixListByTerm.add(simuMatrix);
                    this.pathStMatrixList.add(pathStockPriceMatrix);
                }
            } else if (this.optionType.equals(ExerciseType.Asian)) {
                /**
                 * 针对亚式(平均价, 欧式行权)特殊处理价格模拟矩阵
                 */
                for (int j = 0; j < optionUnderlyingList.size(); j++) {
                    double histSumPrice = prepForAveragePrice(optionTermElement.getStartDate(), optionTermElement.getEndDate());
                    double businessDays = optionTermElement.getStartDate().businessDayNumbers(optionTermElement.getEndDate(), this.calendar)+1;
                    if (optionTermElement.getEndDate().le(qlValDate)) {
                        Matrix averagePriceMatrix = new Matrix(path, 1);
                        averagePriceMatrix.fill(histSumPrice);
                        simuMatrixListByTerm.add(averagePriceMatrix);
                        this.pathStMatrixList.clear();
                        this.pathStMatrixList.add(averagePriceMatrix);
                    } else {
                        Matrix simuMatrix = extractSimuMatrix(currentObs.getObsDates(), optionUnderlyingList.get(0).getSimuSchedule(), this.dailySimuMatrixList.get(j), true);
                        Matrix averagePriceMatrix = simuMatrix.calculateAveragePrice(averageRule, enhancedPrice, histSumPrice, businessDays, isCallOption);
                        simuMatrixListByTerm.add(averagePriceMatrix);
                        this.pathStMatrixList.clear();
                        this.pathStMatrixList.add(averagePriceMatrix);
                    }

                }
            }

            /**
             * Step 3 根据多标的类型判断条款是否触发
             */
            checkTermStatus(simuMatrixListByTerm, optionTermElement);

            /**
             * step 4 加工到期价格矩阵, 敲出价格矩阵, 只加工一次
             */
            if (this.optionUnderlyingList.size() == 1 && i == optionTermList.size()-1 ) {
                this.pathSpotMatrix.fill(this.optionUnderlyingList.get(0).getSpotPrice());
                this.pathStMatrix = this.pathStMatrixList.get(0);
            } else if (isBestPerformance && i == optionTermList.size()-1 ) {
                // 该路径基准标的St、strike、Spot
                initPriceByPath(simuMatrixListByTerm, true);
            } else if (isWorstPerformance && i == optionTermList.size()-1 ) {
                initPriceByPath(simuMatrixListByTerm, false);
            }
        }
    }

    /**
     * @param simuMatrixListByTerm 当前条款类型对应的所有标的价格矩阵
     * @param optionTermElement    当前条款信息
     * @author qianchen
     * @date 2023/6/18
     * @description 根据条款类型判断是否触发
     */
    private void checkTermStatus(List<Matrix> simuMatrixListByTerm, OptionTerms.OptionTermElement optionTermElement) {
        OptionTermType type = optionTermElement.getTerm();
        switch (type) {
            case KNOCK_OUT:
                checkKnockOutStatus(simuMatrixListByTerm, optionTermElement);
                break;
            case KNOCK_IN:
                checkKnockInStatus(simuMatrixListByTerm, optionTermElement);
                break;
            case ACCUMULATE:
                checkAccumulateUnitsStatus(simuMatrixListByTerm, optionTermElement);
                break;
            case DIVIDEND:
                checkDividendYieldStatus(simuMatrixListByTerm, optionTermElement);
                break;
            case PROTECTION:
                checkProectionStatus(simuMatrixListByTerm, optionTermElement);
                break;
            default:
                break;
        }
    }

    /**
     * @author qianchen
     * @date 2023/6/26
     * @description 确认所有路径敲入状态
     */
    private void checkKnockInStatus(List<Matrix> simuMatrixListByTerm, OptionTerms.OptionTermElement optionTermElement) {
        Boolean histKnockInFlag = false;
        boolean upperFlag = optionTermElement.getObsDirection().equals(ExerciseType.UPPER);
        // 判断历史已发生情况
        if (optionTermElement.getStartDate().lt(qlValDate)) {
            histKnockInFlag = checkHistKnockInStatus(optionTermElement);
        }
        if (histKnockInFlag && !isContainAccumulate) {
            pathKnockInMatrix.fill(1);
        } else {
            this.knockInMatrix = new Matrix(simuMatrixListByTerm.get(0).rows(), simuMatrixListByTerm.get(0).cols());
            knockInMatrix.fill(0);
            for (int i = 0; i < this.optionUnderlyingList.size(); i++) {
                Matrix simuMatrix = simuMatrixListByTerm.get(i);
                double barrier = this.optionUnderlyingList.get(i).getSpotPrice() * optionTermElement.getLevel();
                Matrix oneKnockInMatrix = new Matrix();
                oneKnockInMatrix = upperFlag ? oneKnockInMatrix.compareUp(barrier, simuMatrix) : oneKnockInMatrix.compareDown(barrier, simuMatrix);
                knockInMatrix = knockInMatrix.add(oneKnockInMatrix);
            }
            knockInMatrix = knockInMatrix.adjustKnockInMatrix();
            optionTermElement.setStatusMatrix(knockInMatrix);
            Matrix multiMatrix = new Matrix(simuMatrixListByTerm.get(0).cols(), 1);
            multiMatrix.fill(1);
            pathKnockInMatrix = knockInMatrix.mul(multiMatrix);
        }



    }

    /**
     * @author qianchen
     * @date 2023/6/26
     * @description 确认所有路径敲出状态
     */
    private void checkKnockOutStatus(List<Matrix> simuMatrixListByTerm, OptionTerms.OptionTermElement optionTermElement) {
        List<Double> level = this.obsByTerm.get(OptionTermType.KNOCK_OUT).getObsLevel();
        boolean upperFlag = optionTermElement.getObsDirection().equals(ExerciseType.UPPER);
        // 判断历史已发生情况
        if (optionTermElement.getStartDate().lt(qlValDate)) {//观察区开始日小于估值日需要观察历史
            this.histKnockOutFlag = checkHistKnockOutStatus(optionTermElement, level);
        }
        if (this.histKnockOutFlag && !isContainAccumulate) {//估值日前已确认敲出则无需继续判断未来敲出时点
            this.pathKnockOutMatrix.fill(1);
            this.pathKnockOutIndexMatrix.fill(histKnockOutDatePosition);
        } else {
            this.knockOutMatirx = new Matrix(simuMatrixListByTerm.get(0).rows(), simuMatrixListByTerm.get(0).cols());
            knockOutMatirx.fill(0);
            List<Double> levelAfterValDate = adjustTermLevel(OptionTermType.KNOCK_OUT);
            for (int i = 0; i < this.optionUnderlyingList.size(); i++) {
                Matrix simuMatrix = simuMatrixListByTerm.get(i);
                double spot = this.optionUnderlyingList.get(i).getSpotPrice();
//                oneKnockOutMatrix = isCallOption ?
//                        oneKnockOutMatrix.compareUp(barrier, simuMatrix) : oneKnockOutMatrix.compareDown(barrier, simuMatrix);
                Matrix oneKnockOutMatrix = upperFlag ?
                        simuMatrix.compareUp(levelAfterValDate, spot) : simuMatrix.compareDown(levelAfterValDate, spot);

                knockOutMatirx = knockOutMatirx.add(oneKnockOutMatrix);
            }
            knockOutMatirx = knockOutMatirx.adjustKnockOutMatrix(optionUnderlyingList.size());
            optionTermElement.setStatusMatrix(knockOutMatirx);
            Matrix multiMatrix = new Matrix(simuMatrixListByTerm.get(0).cols(), 1);
            multiMatrix.fill(1);
            this.knockOutValDatePosition = calculateValDatePosition(this.obsByTerm.get(OptionTermType.KNOCK_OUT).getObsDates());
            pathKnockOutIndexMatrix = pathKnockOutIndexMatrix.knockOutDate(knockOutMatirx, knockOutValDatePosition); // 获取敲出日
            pathKnockOutMatrix = knockOutMatirx.mul(multiMatrix);
            this.pathKnockOutPriceMatrix = pathKnockOutPriceMatrix.knockOutPrice(knockOutMatirx, simuMatrixListByTerm.get(0));
        }
    }

    /**
     * @author qianchen
     * @date 2023/8/11
     * @description 确认所有路径是否触发分红派息
     * @note 在任一派息观察日, 标的收盘价≥派息价格
     */
    private void checkDividendYieldStatus(List<Matrix> simuMatrixListByTerm, OptionTerms.OptionTermElement optionTermElement) {
        Boolean histFlag = false;
        // 判断历史已发生情况
        if (this.startDate.lt(qlValDate)) {//历史发生的派息默认已经付息，不继续纳入估值现金流考虑范围内
            histFlag = checkHistDividendStatus(optionTermElement);
        }
        pathDividendMatrix = getDividendMatrix(simuMatrixListByTerm, optionTermElement);
//        if (histFlag){
//        Matrix multiMatrix = new Matrix(simuMatrixListByTerm.get(0).cols(),1);
//            multiMatrix.fill(1);
//            pathDividendMatrix = dividendMatrix.mul(multiMatrix);
//            int index = simuMatrixListByTerm.get(0).cols() - 1;
//            pathDividendMatrix = pathDividendMatrix.sub(index);
//        } else {
//            pathDividendMatrix.fill(0);
//        }
    }


    /**
     * @author qianchen
     * @date 2023/10/30
     * @description 确认所有观察日避险状态
     */
    private void checkLizardStatus(List<Matrix> simuMatrixListByTerm, OptionTerms.OptionTermElement optionTermElement) {
        boolean upperFlag = optionTermElement.getObsDirection().equals(ExerciseType.UPPER);
        // 判断历史已发生情况
        if (optionTermElement.getStartDate().lt(qlValDate)) {//观察区开始日小于估值日需要观察历史
            this.histLizardFlag = checkHistLizardStatus(optionTermElement);
        }
        if (!this.histLizardFlag) {
            this.pathLizardMatrix.fill(0);
        } else {
            Matrix lizardMatirx = new Matrix(simuMatrixListByTerm.get(0).rows(), simuMatrixListByTerm.get(0).cols());
            lizardMatirx.fill(0);
            for (int i = 0; i < this.optionUnderlyingList.size(); i++) {
                Matrix simuMatrix = simuMatrixListByTerm.get(i);
                double barrier = this.optionUnderlyingList.get(i).getSpotPrice() * optionTermElement.getLevel();
                Matrix oneLizardMatrix = new Matrix();
                oneLizardMatrix = upperFlag ? oneLizardMatrix.compareUp(barrier, simuMatrix) : oneLizardMatrix.compareDown(barrier, simuMatrix);
                lizardMatirx = lizardMatirx.add(oneLizardMatrix);
            }
            lizardMatirx = lizardMatirx.adjustKnockOutMatrix(optionUnderlyingList.size()); // 所有标的均避险为避险
            lizardMatirx = lizardMatirx.adjustLizardMatrix();
            optionTermElement.setStatusMatrix(lizardMatirx);
            Matrix multiMatrix = new Matrix(simuMatrixListByTerm.get(0).cols(), 1);
            multiMatrix.fill(1);
            this.pathLizardMatrix = lizardMatirx.mul(multiMatrix);
            this.pathLizardMatrix.convert();
        }
    }


    /**
     * @author qianchen
     * @date 2023/9/4
     * @description 确认所有路径累积数量
     * @notes 当前版本最终定期估值日结算数量将在calc方法中完成
     * @notes 最终定期估值日结算数量将在calc方法中完成
     * @notes prep中完成连续观察下的各日累积数量和历史累积数量初始化
     */
    private void checkAccumulateUnitsStatus(List<Matrix> simuMatrixListByTerm, OptionTerms.OptionTermElement optionTermElement) {

        QL.require(this.optionUnderlyingList.size() == 1, "累积奇异期权不支持多标的! ! !");

        this.totalBusinessDays = optionTermElement.getStartDate().businessDayNumbers(optionTermElement.getEndDate(), calendar) + 1;

        if (!(this.histKnockOutFlag && this.ternimateFlag)) { // 历史敲出且提前终止

            /**
             * step 01: 判断历史已发生情况, 已经结算的不考虑, Daily结算的不考虑
             */
            checkHistAccumulateStatus(optionTermElement);

            /**
             * step 02: 判断所有路径所有条款触发情况(敲出 > 敲入，当前版本无其他条款)
             */
            Schedule simuSchedule = new Schedule(optionTermElement.getStartDate(), optionTermElement.getEndDate(), new Period(Frequency.Daily), rds.getCalendars().getCalendar(this.optionUnderlyingList.get(0).getCalendarName()),
                    BusinessDayConvention.Following, BusinessDayConvention.Following, DateGeneration.Rule.Forward, false);
            List<QLDate> accumulateObs = new ArrayList<>();
            List<QLDate> originalObs = simuSchedule.dates();
            // 去除估值日(含)前的观察日
            for (int i = 0; i < originalObs.size(); i++) {
                if (originalObs.get(i).gt(this.qlValDate))
                    accumulateObs.add(originalObs.get(i));
            }
            Matrix simuMatrix = simuMatrixListByTerm.get(0); // 累积奇异期权不支持多标的
            this.eventMatrixByDay = checkAllPathByDay(this.optionTermList, obsByTerm, simuMatrix, optionTermElement, accumulateObs);

            /**
             * step 03: 根据触发情况计算每天累积数量
             */
            List<Double> accumulateAmount = new ArrayList<>();
            List<OptionPayoffs.OptionPayoffElement> current = this.payoffsByTerm.get(OptionTermType.DEFAULT);
            double amount = 0.0;
            for (OptionPayoffs.OptionPayoffElement temp : current) {
                temp.setAmount(this.unitNumbers);
                temp.setBusinessDaysBefore(simuSchedule.dates().size());
                double result = temp.calculateRate();
                amount += temp.getPlus() ? result : -result;
                if (temp.getType().equals(PayoffType.FIXED_DAILY)) {
                    this.target = 0.0;
                    this.fixedPay = temp.getFixedPayment();
                }
            }
            accumulateAmount.add(amount); // Notes: 提前将default情况的累积数量计算好, 位置为第一个. 对应eventMatrixByDay里的index=0

            for (int i = 0; i < optionTermList.size(); i++) {
                OptionTermType type = this.optionTermList.get(i).getTerm();
                if (type.equals(OptionTermType.KNOCK_OUT) || type.equals(OptionTermType.KNOCK_IN)) { // 除了default外只考虑与KnockOu和KnockIn相关触发的累积数量
                    List<OptionPayoffs.OptionPayoffElement> currentPayoff = this.payoffsByTerm.get(type);
                    double units = 0.0;
                    for (OptionPayoffs.OptionPayoffElement temp : currentPayoff) {
                        // TODO 路径依赖的无法在矩阵中判断protection情况, pending优化方案
                        if (temp.getType().equals(PayoffType.PROTECTION) || temp.getType().equals(PayoffType.PROTECTION_FIXED_PAY) || temp.getType().equals(PayoffType.LAST_EXTRA))
                            continue;
                        temp.setAmount(this.unitNumbers);
                        temp.setBusinessDaysAfter(this.startDate.businessDayNumbers(this.maturityDate, this.calendar));
                        double result = temp.calculateRate();
                        if (this.paymentMultiplierFrequency.equals(Frequency.Once) && temp.getTerm().equals(OptionTermType.KNOCK_IN))
                            result = this.unitNumbers * temp.getParticipateRate();
                        units += temp.getPlus() ? result : -result;

                        /**
                         * 针对Daily fixedPay单独处理, 加工好target和fixedPayment
                         */
                        if (temp.getType().equals(PayoffType.FIXED_DAILY)) {
                            this.target = (double) i;
                            this.fixedPay = temp.getFixedPayment();
                        }

                    }
                    accumulateAmount.add(units);
                }
            }
            this.pathAccumulateMatrix = this.eventMatrixByDay.calculateAccumulateAmount(accumulateAmount);

            /**
             * step 04: 根据结算日, 提取simuMatirx, 将结算日的模拟价格存入矩阵
             */
            if (this.settlementFrequency.equals(Frequency.Daily)) {
                valdatesStMatrix = simuMatrix.range(0, simuMatrix.rows(), 0, simuMatrix.cols()); // 忽略估值日
            } else if (this.settlementFrequency.equals(Frequency.Once)) {
                List<QLDate> originalObsDates = this.obsByTerm.get(optionTermElement.getTerm()).getObsDates();  // 结算日, 放在obsByTerm里
                originalObsDates.remove(qlValDate); // 去掉估值日
                valdatesStMatrix = extractSimuMatrix(originalObsDates, simuSchedule, simuMatrix, false);
            } else {
//                throw new EngineException()
            }
        }

    }

    /**
     * @author qianchen
     * @date 2023/10/19
     * @description 确认所有路径保股情况
     */
    private void checkProectionStatus(List<Matrix> simuMatrixListByTerm, OptionTerms.OptionTermElement optionTermElement) {
        if (histKnockOutFlag && !isContainAccumulate) { // 历史已敲出, 合约终止
            this.pathProtectionMatrix.fill(0.0);
        } else if (this.qlValDate.ge(optionTermElement.getEndDate())) { // 保股期已结束, 不再判断, 合约存续默认未触发保股期
            this.pathProtectionMatrix.fill(0.0);
        } else {
            Calendar calendar = rds.getCalendars().getCalendar(this.optionUnderlyingList.get(0).getCalendarName());
            this.pathProtectionMatrix = this.pathKnockOutIndexMatrix.compareDates(optionTermElement.getStartDate(), optionTermElement.getEndDate(), this.obsByTerm.get(OptionTermType.KNOCK_OUT).getObsDates(), calendar);
        }
    }


    private Matrix getDividendMatrix(List<Matrix> simuMatrixListByTerm, OptionTerms.OptionTermElement optionTermElement) {
        Matrix dividendMatrix = new Matrix(simuMatrixListByTerm.get(0).rows(), simuMatrixListByTerm.get(0).cols());
        boolean upperFlag = optionTermElement.getObsDirection().equals(ExerciseType.UPPER);
        dividendMatrix.fill(0);
        for (int i = 0; i < this.optionUnderlyingList.size(); i++) {
            Matrix simuMatrix = simuMatrixListByTerm.get(i);
            double barrier = this.optionUnderlyingList.get(i).getSpotPrice() * optionTermElement.getLevel();
            Matrix oneKnockInMatrix = new Matrix();
            oneKnockInMatrix = upperFlag ? oneKnockInMatrix.compareUp(barrier, simuMatrix, true) : oneKnockInMatrix.compareDown(barrier, simuMatrix, true);
            dividendMatrix = dividendMatrix.add(oneKnockInMatrix);
        }
        dividendMatrix = dividendMatrix.adjustKnockOutMatrix(this.optionUnderlyingList.size());
        return dividendMatrix;
    }

    /**
     * @author qianchen
     * @date 2023/6/26
     * @description 确认估值日前观察日敲出状态
     */
    private Boolean checkHistKnockOutStatus(OptionTerms.OptionTermElement optionTermElement, List<Double> level) {
        double[] knockOutArray = new double[this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size()];
        boolean upperFlag = optionTermElement.getObsDirection().equals(ExerciseType.UPPER);

        // 若为多标的, 默认所有标的的观察日列表相同, 使用obsByTerm.getTerm
        for (int j = 0; j < this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size(); j++) {
            QLDate date = this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().get(j);
            if (date.gt(qlValDate)) {
                break;
            }
            double index = level.get(j);

            int flag = 0;
            for (int i = 0; i < this.optionUnderlyingList.size(); i++) {
                double price = this.optionUnderlyingList.get(i).getUnderlyingPrice().getPriceByDate(date);
                double barrier = Math.round(this.optionUnderlyingList.get(i).getSpotPrice() * index);
                // 根据交易的看涨看跌方向判断敲出方向
                flag += upperFlag ? (price >= barrier ? 1 : 0) : (price <= barrier ? 1 : 0);
            }
            // 所有标的敲出判定为该观察日敲出
            knockOutArray[j] = flag == this.optionUnderlyingList.size() ? 1 : 0;
        }

        double sum = Arrays.stream(knockOutArray).sum();
        for (int j = 0; j < this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size(); j++) {
            if (knockOutArray[j] > 0) {
                this.histKnockOutDatePosition = j;
                this.histKnockOutDate = this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().get(j);
                break;
            }
        }
        return sum > 0;
    }

    /**
     * @author qianchen
     * @date 2023/11/7
     * @description
     */
    private Boolean checkHistKnockOutLayerStatus(OptionTerms.OptionTermElement optionTermElement, List<Double> level) {
        double[] knockOutArray = new double[this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size()];
        boolean upperFlag = optionTermElement.getObsDirection().equals(ExerciseType.UPPER);

        // 若为多标的, 默认所有标的的观察日列表相同, 使用obsByTerm.getTerm
        for (int j = 0; j < this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size(); j++) {
            QLDate date = this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().get(j);
            if (date.gt(qlValDate)) {
                break;
            }
            double index = level.get(j);

            int flag = 0;
            for (int i = 0; i < this.optionUnderlyingList.size(); i++) {
                double price = this.optionUnderlyingList.get(i).getUnderlyingPrice().getPriceByDate(date);
                double barrier = this.optionUnderlyingList.get(i).getSpotPrice() * index;
                // 根据交易的看涨看跌方向判断敲出方向
                flag += upperFlag ? (price > barrier ? 1 : 0) : (price < barrier ? 1 : 0);
            }
            // 所有标的敲出判定为该观察日敲出
            knockOutArray[j] = flag == this.optionUnderlyingList.size() ? 1 : 0;
        }

        double sum = Arrays.stream(knockOutArray).sum();
        for (int j = 0; j < this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size(); j++) {
            if (knockOutArray[j] > 0) {
                this.histKnockOutLayerDatePosition = j;
                this.histKnockOutLayerDate = this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().get(j);
                break;
            }
        }
        return sum > 0;
    }

    /**
     * @author qianchen
     * @date 2023/6/26
     * @description 确认估值日前观察日敲入状态
     */
    private Boolean checkHistKnockInStatus(OptionTerms.OptionTermElement optionTermElement) {
        double[] knockInArray = new double[this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size()];
        double index = optionTermElement.getLevel();
        boolean upperFlag = optionTermElement.getObsDirection().equals(ExerciseType.UPPER);

        // 若为多标的, 默认所有标的的观察日列表相同, 使用obsByTerm.getTerm
        for (int j = 0; j < this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size(); j++) {
            QLDate date = this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().get(j);
            if (date.gt(qlValDate))
                break;

            int flag = 0;
            for (int i = 0; i < this.optionUnderlyingList.size(); i++) {
                double price = this.optionUnderlyingList.get(i).getUnderlyingPrice().getPriceByDate(date);
                double barrier = Math.round(this.optionUnderlyingList.get(i).getSpotPrice() * index);
                // 根据交易的看涨看跌方向判断敲出方向
                flag += upperFlag ? (price >= barrier ? 1 : 0) : (price <= barrier ? 1 : 0);
            }
            // 所有标的敲出判定为该观察日敲出
            knockInArray[j] = flag == 0 ? 0 : 1;
        }

        double sum = Arrays.stream(knockInArray).sum();
        return sum > 0;
    }


    /**
     * @author qianchen
     * @date 2023/11/7
     * @description 确认历史派息触发状态, 派息分红可以进行多次
     */
    private Boolean checkHistDividendStatus(OptionTerms.OptionTermElement optionTermElement) {
        double[] dividendArray = new double[this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size()];
        double index = optionTermElement.getLevel();
        int position = 0;

        // 若为多标的, 默认所有标的的观察日列表相同, 使用obsByTerm.getTerm
        for (int j = 0; j < this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size(); j++) {
            QLDate date = this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().get(j);
            if (date.gt(qlValDate))
                break;

            int flag = 0;
            for (int i = 0; i < this.optionUnderlyingList.size(); i++) {
                double price = this.optionUnderlyingList.get(i).getUnderlyingPrice().getPriceByDate(date);
                double barrier = this.optionUnderlyingList.get(i).getSpotPrice() * index;
                // 根据交易的看涨看跌方向判断敲出方向
                flag += price >= barrier ? 1 : 0;
            }
            // 所有标的观察日收盘价均大于barrier判断为触发
            dividendArray[j] = flag == this.optionUnderlyingList.size() ? 1 : 0;
            position++;
        }

        double sum = Arrays.stream(dividendArray).sum();
        return sum == position;
    }

    /**
     * @author qianchen
     * @date 2023/10/30
     * @description 确认估值日前观察日避险状态
     */
    private Boolean checkHistLizardStatus(OptionTerms.OptionTermElement optionTermElement) {
        double[] lizardArray = new double[this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size()];
        boolean upperFlag = optionTermElement.getObsDirection().equals(ExerciseType.UPPER);
        double index = optionTermElement.getLevel();

        // 若为多标的, 默认所有标的的观察日列表相同, 使用obsByTerm.getTerm
        for (int j = 0; j < this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().size(); j++) {
            QLDate date = this.obsByTerm.get(optionTermElement.getTerm()).getObsDates().get(j);
            if (date.gt(qlValDate)) {
                break;
            }

            int flag = 0;
            for (int i = 0; i < this.optionUnderlyingList.size(); i++) {
                double price = this.optionUnderlyingList.get(i).getUnderlyingPrice().getPriceByDate(date);
                double barrier = this.optionUnderlyingList.get(i).getSpotPrice() * index;
                // 根据交易的看涨看跌方向判断避险方向
                flag += upperFlag ? (price > barrier ? 1 : 0) : (price < barrier ? 1 : 0);
            }
            // 所有标的避险成功判定为该避险成功
            lizardArray[j] = flag == this.optionUnderlyingList.size() ? 1 : 0;
        }

        double sum = Arrays.stream(lizardArray).sum();  // > 0为避险成功
        return sum > 0;
    }

    /**
     * @author qianchen
     * @date 2023/9/4
     * @description 确认估值日前观察日的累积状态
     */
    private void checkHistAccumulateStatus(OptionTerms.OptionTermElement optionTermElement) {
        double protectionAmount = 0.0;
        if (this.settlementFrequency.equals(Frequency.Daily) && optionTermElement.getStartDate().le(qlValDate) && !isReVal) {
            this.histAccuAmount = 0.0;
            // Daily Pay, 历史情况已经结算, 估值不再考虑
            OptionUnderlyings.OptionUnderlyingElement oneUnderlying = this.optionUnderlyingList.get(0);
            Price price = oneUnderlying.getUnderlyingPrice();
            double spot = oneUnderlying.getSpotPrice();

            List<QLDate> obsDates = new ArrayList<>();
            if (optionTermElement.getStartDate().eq(qlValDate)) {
                obsDates.add(qlValDate);
            } else {
                obsDates = new Schedule(optionTermElement.getStartDate(), qlValDate, new Period(Frequency.Daily), rds.getCalendars().getCalendar(this.optionUnderlyingList.get(0).getCalendarName()),
                        BusinessDayConvention.Following, BusinessDayConvention.Following, DateGeneration.Rule.Forward, false).dates();
            }

            for (int i = 0; i < obsDates.size(); i++) {
                QLDate currentDate = obsDates.get(i);
                double St = price.getPriceByDate(currentDate);
                double strike = this.optionUnderlyingList.get(0).getSpotPrice() * this.optionTermList.get(optionTermList.size()-1).getLevel();
                // 估值日当天作为历史判断
                if (currentDate.gt(qlValDate))
                    break;
                // 判断各日条款触发情况
                for (OptionTerms.OptionTermElement temp : this.optionTermList) {
                    OptionTerms terms = new OptionTerms();
                    Boolean currentFlag = terms.checkFlagByDay(currentDate, temp, price.getPriceByDate(currentDate), spot, this.isCallOption);
                    temp.setTrueByDay(currentFlag);
                }
                // 计算当前日期累积数量
                OptionTerms terms = new OptionTerms();
                OptionTermType target = terms.getFirstByDay(this.optionTermList);
                List<OptionPayoffs.OptionPayoffElement> optionPayoffList = this.payoffsByTerm.get(target);
                double amount = 0.0;
                double fixedAmount = 0.0;
                boolean isFixedpay = false;
                for (OptionPayoffs.OptionPayoffElement temp : optionPayoffList) {
                    temp.setAmount(this.unitNumbers);
                    double result = temp.calculateRate();
                    amount += temp.getPlus() ? result : -result;
                    if (temp.getType().equals(PayoffType.FIXED_DAILY)) {
                        isFixedpay = true;
                        fixedAmount = temp.getFixedPayment();
                    }
                }
                double payment = isFixedpay ? fixedAmount * amount : amount * (St - strike);
                this.histAccuAmount += payment;
            }
        } else if (this.qlValDate.lt(optionTermElement.getStartDate())) {
            // 观察开始日在估值日之后, 不判断历史累计情况
            this.histAccuUnits = 0.0;
        } else {
            this.histAccuUnits = 0.0;
            OptionUnderlyings.OptionUnderlyingElement oneUnderlying = this.optionUnderlyingList.get(0);
            Price price = oneUnderlying.getUnderlyingPrice();
            double spot = oneUnderlying.getSpotPrice();

//            List<QLDate> obsDates = this.obsByTerm.get(optionTermElement.getTerm()).getObsDates();
            List<QLDate> obsDates = new ArrayList<>();
            if (optionTermElement.getStartDate().eq(qlValDate)) {
                obsDates.add(qlValDate);
            } else {
                obsDates = new Schedule(optionTermElement.getStartDate(), qlValDate, new Period(Frequency.Daily), rds.getCalendars().getCalendar(this.optionUnderlyingList.get(0).getCalendarName()),
                        BusinessDayConvention.Following, BusinessDayConvention.Following, DateGeneration.Rule.Forward, false).dates();
            }
            for (int i = 0; i < obsDates.size(); i++) {
                QLDate currentDate = obsDates.get(i);

                // 估值日当天作为历史判断
                if (currentDate.gt(qlValDate))
                    break;
                // 判断各日条款触发情况
                for (OptionTerms.OptionTermElement temp : this.optionTermList) {
                    OptionTerms terms = new OptionTerms();
                    if (!obsByTerm.get(temp.getTerm()).getObsDates().contains(currentDate)) {
                        temp.setTrueByDay(false);
                    } else {
                        Boolean currentFlag = terms.checkFlagByDay(currentDate, temp, price.getPriceByDate(currentDate), spot, this.isCallOption);
                        temp.setTrueByDay(currentFlag);
                    }
                }
                // 计算当前日期累积数量
                OptionTerms terms = new OptionTerms();
                OptionTermType target = terms.getFirstByDay(this.optionTermList);
                List<OptionPayoffs.OptionPayoffElement> optionPayoffList = this.payoffsByTerm.get(target);
                double amount = 0.0;
                for (OptionPayoffs.OptionPayoffElement temp : optionPayoffList) {
                    temp.setAmount(this.unitNumbers);
                    if (target.equals(OptionTermType.KNOCK_OUT) && temp.getType().equals(PayoffType.PROTECTION)) {
                        // 计算后半段Protection收益
                        temp.setBusinessDaysAfter(this.startDate.businessDayNumbers(currentDate, this.calendar));
                        temp.setSt(price.getPriceByDate(currentDate));
                        double result = temp.calculateRate();
                        protectionAmount = temp.getPlus() ? result : -result;
                    } else {
                        double result = temp.calculateRate();
                        amount += temp.getPlus() ? result : -result;
                    }
                }
                this.histAccuUnits += amount;

                // 考虑历史protection支付金额
                if (histKnockOutFlag) {
                    if (currentDate.eq(this.histKnockOutDate) && !this.returnFlag) {
                        double strike = this.optionTermList.get(optionTermList.size()-1).getLevel() * spot;
                        this.histAccuPaymentAmount = this.histAccuUnits * (price.getPriceByDate(histKnockOutDate) - strike) + protectionAmount;
                        break;
                    }
                }
            }
        }
    }

    /**
     * @param dateList        该条款的观察日
     * @param simuSchedule    用于生成该标的daily模拟矩阵的DateList
     * @param dailySimuMatrix 该标的daily模拟矩阵
     * @param includeValDay   daily模拟矩阵是否包括估值日
     * @author qianchen
     * @date 2023/6/17
     * @description 按照观察日提取价格矩阵
     */
    private Matrix extractSimuMatrix(List<QLDate> dateList, Schedule simuSchedule, Matrix dailySimuMatrix, Boolean includeValDay) {
        ArrayList<Integer> extractTargets = compareListPosition(dateList, simuSchedule.dates());
//        if (dateList.get(0).eq(this.qlValDate))
//            return dailySimuMatrix.extractMatrix(extractTargets, true); // 若目标提取日中包含估值日, 应考虑
        return dailySimuMatrix.extractMatrix(extractTargets, includeValDay);
    }


    /**
     * @author qianchen
     * @date 2023/6/20
     * @description 获取当前路径触发条款index
     */
    private int getFirst(List<OptionTerms.OptionTermElement> optionTermList) {
        for (OptionTerms.OptionTermElement temp : optionTermList) {
            if (temp.getTrue())
                return optionTermList.indexOf(temp);
        }
        return optionTermList.size() + 1;
    }


    /**
     * @author qianchen
     * @date 2023/6/20根据观察收益表现水平初始化payoff使用的spotPrice和St
     * @description
     */
    private void initPriceByPath(List<Matrix> simuMatrixListByTerm, Boolean isBestPerformance) {
        final Matrix result = simuMatrixListByTerm.get(0);
        int addr = 0;
        // Iterate all St by path
        for (int row = 0; row < result.rows(); row++) {
            ArrayList<Double> performanceList = new ArrayList<>();
            for (int i = 0; i < optionUnderlyingList.size(); i++) {
                double St = this.pathStMatrixList.get(i).get(row, 0);
                double spot = optionUnderlyingList.get(i).getSpotPrice();
                performanceList.add(St / spot);
            }
            // find index of max or min underlying price
            int index;
            if (isBestPerformance) {
                index = performanceList.indexOf(Collections.max(performanceList));
            } else {
                index = performanceList.indexOf(Collections.min(performanceList));
            }
            // 获取到目标标的当前path的最后一列价格
            double actualSt = this.pathStMatrixList.get(index).get(row, 0);
            double spotPrice = optionUnderlyingList.get(index).getSpotPrice();
            this.pathStMatrix.set(row, 0, actualSt);
            this.pathSpotMatrix.set(row, 0, spotPrice);
        }
    }

    /**
     * @author qianchen
     * @date 2024/5/23
     * @description 亚式期权调整价格逻辑
     */
    private double prepForAveragePrice(QLDate startDate, QLDate endDate) {
        double result = 0.0;
        if (qlValDate.lt(startDate))
            return result;
        QL.require(this.optionUnderlyingList.size() == 1, "亚式期权不支持多标的! ! !");
        Price underlyingPrice = this.optionUnderlyingList.get(0).getUnderlyingPrice();
        for (int i = 0; ;i++) {
            QLDate current = startDate.add(i);
            if (!this.calendar.isBusinessDay(current)) {
                continue;
            }
            if (current.gt(qlValDate) || current.gt(endDate))
                break;

            double price = 0.0;
            if (this.averageRule.equals(AverageRule.ENHANCE_AVERAGE)) {
                price = isCallOption ? Math.max(underlyingPrice.getPriceByDate(current), enhancedPrice) : Math.min(underlyingPrice.getPriceByDate(current), enhancedPrice);
            } else {
                price = underlyingPrice.getPriceByDate(current);
            }
            result += price;
        }
        return result;
    }

    private List<Double> adjustTermLevel(OptionTermType type) {
        List<Double> level = new ArrayList<>();
        OneForAll.OptionObsByTerm obs = this.obsByTerm.get(type);
        for (int i = 0; i < obs.getObsDates().size(); i++) {
            QLDate date = obs.getObsDates().get(i);
            if (date.gt(this.qlValDate))
                level.add(obs.getObsLevel().get(i));
        }
        return level;
    }

    /**
     * @author qianchen
     * @date 2023/11/7
     * @description 根据敲出日期判断避险情况
     */
    private double getLizardReuslt(int index) {
        double result = 0.0;
        // 需比较Lizard观察结束日与敲出日位置
        int knockOutIndex = (int) pathKnockOutIndexMatrix.get(index, 0);
        QLDate knockOutDate = obsByTerm.get(OptionTermType.KNOCK_OUT).getObsDates().get(knockOutIndex);
        QLDate lizardEndDate = obsByTerm.get(OptionTermType.LIZARD).getObsDates().get(obsByTerm.get(OptionTermType.LIZARD).getObsDates().size() - 1);
        if (knockOutDate.le(lizardEndDate)) {
            result = 0.0;
        } else {
            result = this.pathLizardMatrix.get(index, 0);
        }
        return result;
    }

    /**
     * @author qianchen
     * @date 11/08/2023
     * @description 重估值调整S0
     */
    public void setOptionUnderlyingListForPrice(MrChangeType mrChangeType, double change) {
        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
            double price = underlyingElement.getUnderlyingPrice().getPriceByDate(qlValDate);
            // 根据变动类型计算调整后的价格
            switch (mrChangeType) {
                case ABSOLUTE:
                    price += change;
                    this.change = change;
                    break;
                case PERCENTAGE:
                    this.change = Math.abs( price * change);
                    price *= (1 + change);
                    break;
            }
            underlyingElement.getUnderlyingPrice().addPriceByDate(qlValDate, price);
        }
    }

    /**
     * @author qianchen
     * @date 11/08/2023
     * @description 重估值调整vol
     */
    public void setOptionUnderlyingListForVol(MrChangeType mrChangeType, double change) {
        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
            double volatility = underlyingElement.getVolatility();
            switch (mrChangeType) {
                case ABSOLUTE:
                    volatility += change;
                    this.change = change;
                    break;
                case PERCENTAGE:
                    volatility *= (1 + change);
                    this.change = Math.abs (volatility * change);
                    break;
            }
            underlyingElement.setVolatility(volatility);
        }
    }


    public void setOptionUnderlyingListForSimulationRate(MrChangeType mrChangeType, double change) {
        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
            double rfr = underlyingElement.getRfr();
            switch (mrChangeType) {
                case ABSOLUTE:
                    rfr += change;
                    this.change = change;
                    this.discountCurve.setSpread(change);
                    break;
                case PERCENTAGE:
                    rfr *= (1 + change);
                    this.change = Math.abs (rfr * change);
                    break;
            }
            underlyingElement.setRfr(rfr);
            if (!underlyingElement.getType().equals(PriceType.STOCK) && !underlyingElement.getType().equals(PriceType.STOCK_INDEX)) {
                underlyingElement.setDividend(rfr);
            }
        }
    }


//    /**
//     * @author Zi
//     * @date 11/08/2023
//     * @description 重估值调整S0
//     */
//    public void setOptionUnderlyingListForPrice(double scalar) {
//        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
//            double adjPrice = underlyingElement.getUnderlyingPrice().getPriceByDate(qlValDate) + scalar;
//            underlyingElement.getUnderlyingPrice().addPriceByDate(qlValDate, adjPrice);
//        }
//    }

//    /**
//     * @author Zi
//     * @date 11/08/2023
//     * @description 重估值调整vol
//     */
//    public void setOptionUnderlyingListForVol(double scalar) {
//        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
//            underlyingElement.setVolatility(underlyingElement.getVolatility() + scalar);
//        }
//    }


    private void prepareForDeltaOrGamma(IndicatorType indicatorType) {

        GreekSensitivity greekSensitivity = rds.getGreekSensitivity(indicatorType);
        MrChangeType mrChangeType = MrChangeType.ABSOLUTE;
        logger.debug("交易号:"+this.instrument.getInstId()+"开始计算" + indicatorType);
        if (greekSensitivity != null) {
            mrChangeType = greekSensitivity.getMrChangeType();
            this.epsilon = greekSensitivity.getChange();
        }
        // 保存初始价格数据
        setOriginalPrice();

        // 如果没有配置DLETA/GAMMA变动参数，默认ABSOLUTE, epsilon = 10计算
        setOptionUnderlyingListForPrice(mrChangeType, epsilon);
        pv_up = calculatePV();
        resetPrice(); // 还原价格

        setOptionUnderlyingListForPrice(mrChangeType, -epsilon);
        pv_down = calculatePV();
        resetPrice(); // 还原价格

        logger.debug("交易号:"+this.instrument.getInstId()+"S0变动后PV结果： UP " + pv_up + " down " + pv_down + "变动类型 " + mrChangeType + " S0变动 " + epsilon);
    }

    /**
     * @author qianchen
     * @date 11/08/2023
     * @description 通过重估值法计算复杂期权的Delta值
     */
    private Double calculateDelta() {
        if (delta == 0.0) {
            prepareForDeltaOrGamma(IndicatorType.DELTA);
            this.delta = (pv_up - pv_down) / (2 * Math.abs(this.change));
            logger.debug("交易号:"+this.instrument.getInstId()+"自定义期权Delta: " + this.delta);
        }
        return delta;
    }


    private Double calculateDeltaAmount(Boolean isAdjusted) {
        if (this.delta == 0.0) {
            this.delta = calculateDelta();
        }
        OptionUnderlyings.OptionUnderlyingElement oneUnderlying = this.optionUnderlyingList.get(0);
        Double S0 = oneUnderlying.getUnderlyingPrice().getPriceByDate(qlValDate);
        Double vol = oneUnderlying.getVolatility();
        Double yearFrac = oneUnderlying.getYearFrac();
        if (isAdjusted) {
            return this.delta * S0 * position * vol / Math.sqrt(yearFrac);
        } else {
            return this.delta * S0 * this.position;
        }

    }

    /**
     * @author qianchen
     * @date 11/08/2023
     * @description 通过重估值法计算复杂期权的Gamma值
     */
    private Double calculateGamma() {
        if (this.gamma == 0.0) {
            prepareForDeltaOrGamma(IndicatorType.GAMMA);
            this.gamma = (pv_up + pv_down - 2 * pv) / Math.pow(this.change, 2);
            logger.debug("交易号:"+this.instrument.getInstId()+"自定义期权Gamma:" + this.gamma);
        }

        return this.gamma;
    }

    private Double calculateGammaAmount(Boolean isAdjusted) {
        if (this.gamma == 0) {
            calculateGamma();
        }

        OptionUnderlyings.OptionUnderlyingElement oneUnderlying = this.optionUnderlyingList.get(0);
        Double S0 = oneUnderlying.getUnderlyingPrice().getPriceByDate(qlValDate);
        Double vol = oneUnderlying.getVolatility();
        Double yearFrac = oneUnderlying.getYearFrac();

        if (isAdjusted) {
            return 0.5 * gamma * Math.pow(S0 * vol / Math.sqrt(yearFrac), 2);
        } else {
            return position * gamma * Math.pow(S0, 2) / 100;
        }
    }

    /**
     * @author qianchen
     * @date 11/08/2023
     * @description 通过重估值法计算复杂期权的Vega值
     */
    private Double calculateVega() {
        if (this.vega == 0) {
            logger.debug("交易号:"+this.instrument.getInstId()+"Vega calculation starts");
            double up, down;
            GreekSensitivity greekSensitivity = rds.getGreekSensitivity(IndicatorType.VEGA);
            // 如果没有配置VEGA变动参数, 默认ABSOLUTE, bump_bp = 0.0001;
            MrChangeType mrChangeType = MrChangeType.ABSOLUTE;

            if (greekSensitivity != null) {
                mrChangeType = greekSensitivity.getMrChangeType();
                this.bump_bp = greekSensitivity.getChange();
            }

            // 保存初始波动率数据
            setOriginalVolatility();

            setOptionUnderlyingListForVol(mrChangeType, bump_bp);
            up = calculatePV();
            resetVolatility();// 还原波动率数据

            setOptionUnderlyingListForVol(mrChangeType, -bump_bp);
            down = calculatePV();
            resetVolatility();// 还原波动率数据

            this.vega = (up - down) / (2.0 * Math.abs(this.change) * 100.0);
            logger.debug("交易号:"+this.instrument.getInstId()+"Vega: up: {}, down: {}, 波动率变动类型: {}, 波动率变动值: {}", up, down, mrChangeType, bump_bp);
            logger.debug("交易号:"+this.instrument.getInstId()+"自定义期权Vega:" + this.vega);
        }
        return this.vega;
    }

    /**
     * @author qianchen
     * @date 11/08/2023
     * @description 通过重估值法计算复杂期权的Theta值
     * pending方案
     */
    private Double calculateTheta() {
        if (this.theta == 0) {
            logger.debug("交易号:"+this.instrument.getInstId()+"Theta calculation starts");
            double up = 0.0d, down = 0.0d;
            GreekSensitivity greekSensitivity = rds.getGreekSensitivity(IndicatorType.THETA);
            // 默认ABSOLUTE变动1天
            MrChangeType mrChangeType = MrChangeType.ABSOLUTE;
            Double change = 1.0;
            if (greekSensitivity != null) {
                mrChangeType = greekSensitivity.getMrChangeType();
                change = greekSensitivity.getChange();
            }

            switch (mrChangeType) {
                case ABSOLUTE:
                    this.term = this.dayCounter.yearFraction(qlValDate, this.endDate.add(change.intValue()));
                    up = calculatePV();
                    this.term = this.dayCounter.yearFraction(qlValDate, this.endDate.add(-change.intValue()));
                    down = calculatePV();
                    this.change = this.dayCounter.yearFraction(qlValDate, this.endDate.add(change.intValue())) - this.dayCounter.yearFraction(qlValDate, this.endDate.add(-change.intValue()));
                    break;
                case PERCENTAGE:
                    this.term = term * (1 + change);
                    up = calculatePV();
                    this.term = ( term / (1 + change) ) * (1 - change);
                    down = calculatePV();
                    this.term = this.dayCounter.yearFraction(qlValDate, this.endDate); // 还原年化时间
                    this.change = this.term * change;
                    break;
            }
            this.term = this.dayCounter.yearFraction(qlValDate, this.endDate); // 还原年化时间
            QL.require(change != 0.0, "交易号:"+this.instrument.getInstId()+"敏感度Theta配置错误, 日期变动前后存在节假日! !");
            this.theta = (up - down) / (2 * this.change);
            logger.debug("交易号:"+this.instrument.getInstId()+"theta: up: {}, down: {}", up, down);
            logger.debug("交易号:"+this.instrument.getInstId()+"自定义期权theta:" + this.theta);
        }
        return this.theta;
    }

    /**
     * @author qianchen
     * @date 14/08/2023
     * @description
     */
    private Double calculateRho() {
        logger.debug("交易号:"+this.instrument.getInstId()+"Rho calculation starts");
        double up = 0.0d, down = 0.0d;
        GreekSensitivity greekSensitivity = rds.getGreekSensitivity(IndicatorType.RHO);
        // 默认ABSOLUTE变动0.0001
        MrChangeType mrChangeType = MrChangeType.ABSOLUTE;
        double change = bump_bp;
        double originalRfr = this.rfr;
        if (greekSensitivity != null) {
            mrChangeType = greekSensitivity.getMrChangeType();
            change = greekSensitivity.getChange();
        }

        setOriginalSimulationRate();

        setOptionUnderlyingListForSimulationRate(mrChangeType, change);
        up = calculatePV();
        resetSimulationRate();

        setOptionUnderlyingListForSimulationRate(mrChangeType, -change);
        down = calculatePV();
        resetSimulationRate();

        this.rfr = originalRfr; // 还原初始值
        this.rho =  (up - down) / (2.0 * Math.abs(this.change) * 100.0);
        logger.debug("交易号:"+this.instrument.getInstId()+"rho: up: {}, down: {}", up, down);
        logger.debug("交易号:"+this.instrument.getInstId()+"自定义期权Rho: " + this.rho);
        return rho;
    }

    private Double calculateOptionVolatility(OptionUnderlyings.OptionUnderlyingElement oneUnderlying) {
        VolatilityType type = oneUnderlying.getVolType();
        PriceType assetType = oneUnderlying.getType();
        logger.debug("交易号:"+this.instrument.getInstId()+"奇异期权({}标的){}波动率算法: {}", oneUnderlying.getType(), this.instrument.getInstId(), type);
        double result = 0.0;
        try {
            switch (type) {
                case HISTORICAL:
                    double dayNumber;
                    PriceType volPriceType;
                    if (assetType.equals(PriceType.STOCK) || assetType.equals(PriceType.STOCK_INDEX)) {
                        volPriceType = oneUnderlying.getVolPriceType();
                    } else {
                        volPriceType = assetType;
                    }
                    Price adjPrice = super.mds.getTypePrices(volPriceType, oneUnderlying.getUnderlyingCode());  // 计算波动率价格
                    if (oneUnderlying.getVolBaseNumber() != null) {
                        dayNumber = oneUnderlying.getVolBaseNumber();
                    } else {
                        dayNumber = maturityDate.sub(qlValDate);
                    }
                    double yearFrac = oneUnderlying.getYearFrac();
                    Calendar calendar = rds.getCalendars().getCalendar(oneUnderlying.getCalendarName());
                    result = HistoricalVol.calculateVolatility(qlValDate, dayNumber, adjPrice, adjPrice, calendar, yearFrac);
                    break;
                case IMPLIED:
                    String surfaceId = oneUnderlying.getSurfaceName();
                    SurfaceType surfaceType = assetType.equals(PriceType.FX_SPOT_CFETS) || assetType.equals(PriceType.FX_SPOT_PBC) ?
                            SurfaceType.FX : SurfaceType.COM;
                    Surface surface = super.mds.getVolSurface(surfaceId, surfaceType).getOneDateSurface(qlValDate);
//                    double moneyness = oneUnderlying.getStrike() / oneUnderlying.getUnderlyingPrice().getPriceByDate(qlValDate);
                    double volTerm = surface.getDayCounter().yearFraction(this.qlValDate, this.maturityDate);
                    result = surface.getVol(volTerm, 1.0); // smile-off 曲面插值
                    break;
                case TRADE:
                    result = oneUnderlying.getVolatility();
                    break;
                default:
                    break;
            }
        } catch (EngineException e) {
            EngineException ne = new EngineException(e, EngineCodeEnums.ERROR_UNKNOWN_VOLATILITY, this.instrument.getInstId());
            logger.error(ne.getMessage(), e);
            logger.error("tradeNo = " + this.instrument.getInstId() + " error!!!", e);
        }

        return result;
    }


    private Double calculateVolatilityForVegaCapital() {
        OptionUnderlyings.OptionUnderlyingElement oneUnderlying = this.optionUnderlyingList.get(0);
        double vol = 0.0, days;
        PriceType volPriceType;
        PriceType assetType = oneUnderlying.getType();
        if (assetType.equals(PriceType.STOCK) || assetType.equals(PriceType.STOCK_INDEX)) {
            volPriceType = oneUnderlying.getVolPriceType();
        } else {
            volPriceType = assetType;
        }
        Price adjPrice = super.mds.getTypePrices(volPriceType, oneUnderlying.getUnderlyingCode());  // 计算波动率价格

        try {
            Calendar calendar = rds.getCalendars().getCalendar(oneUnderlying.getCalendarName());
            vol = HistoricalVol.calculateVolatilityforVegaCapital(qlValDate, adjPrice, calendar);
            return vol;
        } catch (EngineException e) {
            if (e.equals(new EngineException(EngineCodeEnums.ERROR_UNMATCHED_DAYNUMBER)))
                return 0.3;
        } catch (Exception ee) {
            logger.warn("交易号:"+this.instrument.getInstId()+"用于vega敏感度计量的波动率计算失败, 使用0.3兜底");
            return  0.3;
        }
        return 0.3;
    }


    /**
     * @author jiaming
     * @date 02/04/2024
     * @description 初始市场数据赋值，用于后续还原
     */
    public void setOriginalPrice() {
        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
            double price = underlyingElement.getUnderlyingPrice().getPriceByDate(qlValDate);
            Price originalPrice = underlyingElement.getOrignalunderlyingPrice();
            originalPrice.addPriceByDate(qlValDate, price);
        }
    }

    /**
     * @author jiaming
     * @date 02/04/2024
     * @description 还原OptionUnderlyingElement中修改后的价格S0数据
     */
    private void resetPrice() {
        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
            Price originalPrice = underlyingElement.getOrignalunderlyingPrice();
            double price = originalPrice.getPriceByDate(qlValDate);
            underlyingElement.getUnderlyingPrice().addPriceByDate(qlValDate, price);
        }
    }

    /**
     * @author jiaming
     * @date 02/04/2024
     * @description 还原OptionUnderlyingElement中波动率数据
     */
    private void resetVolatility() {
        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
            double originalVolatility = underlyingElement.getOriginalVolatility();
            underlyingElement.setVolatility(originalVolatility);
        }
    }

    private void resetSimulationRate() {
        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
            double SimulationRate = underlyingElement.getOriginalRfr();
            underlyingElement.setRfr(SimulationRate);
            this.discountCurve.setSpread(0.0);

            if (!underlyingElement.getType().equals(PriceType.STOCK) && !underlyingElement.getType().equals(PriceType.STOCK_INDEX)) {
                underlyingElement.setDividend(SimulationRate);
            }
        }
    }



    /**
     * @author jiaming
     * @date 02/04/2024
     * @description 初始波动率赋值，用于后续还原
     */
    public void setOriginalVolatility() {
        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
            double volatility = underlyingElement.getVolatility();
            underlyingElement.setOriginalVolatility(volatility);
        }
    }

    public void setOriginalSimulationRate() {
        for (OptionUnderlyings.OptionUnderlyingElement underlyingElement : optionUnderlyingList) {
            double r = underlyingElement.getRfr();
            underlyingElement.setOriginalRfr(r);
        }
    }


    /**
     * @author qianchen
     * @date 2024/4/12
     * @description 可根据不同项目需求调整
     */
    public void initAccumulateTerm() {
        if (this.isContainAccumulate) {
            OptionTerms.OptionTermElement knockInTerm = new OptionTerms().new OptionTermElement();
            OptionTerms.OptionTermElement protectionTerm = new OptionTerms().new OptionTermElement();
            OptionTerms.OptionTermElement newAccumulateTerm = new OptionTerms().new OptionTermElement();
            for (int i = 0; i < this.optionTermList.size(); i++) {
                if (optionTermList.get(i).getTerm().equals(OptionTermType.KNOCK_IN)) {
                    knockInTerm = optionTermList.get(i);
                    break;
                }
            }

            newAccumulateTerm.setTerm(OptionTermType.ACCUMULATE);
            newAccumulateTerm.setLevel(knockInTerm.getLevel());
            newAccumulateTerm.setStartDate(knockInTerm.getStartDate());
            newAccumulateTerm.setEndDate(knockInTerm.getEndDate());
            newAccumulateTerm.setObsFrequency(this.settlementFrequency); // 结算频率

            List<OptionPayoffs.OptionPayoffElement> KnockOutPayoff = this.payoffsByTerm.get(OptionTermType.KNOCK_OUT);
            for (int i = 0; i < KnockOutPayoff.size(); i++) {
                OptionPayoffs.OptionPayoffElement payoff = KnockOutPayoff.get(i);
                if (payoff.getType().equals(PayoffType.PROTECTION) || payoff.getType().equals(PayoffType.PROTECTION_FIXED_PAY)) {
                    protectionTerm.setTerm(OptionTermType.PROTECTION);
                    protectionTerm.setStartDate(optionTermList.get(0).getStartDate());
                    protectionTerm.setEndDate(optionTermList.get(0).getEndDate());
                    protectionTerm.setObsFrequency(optionTermList.get(0).getObsFrequency());
                    protectionTerm.setObsDirection(ExerciseType.NO_DIRECTION);
                    protectionTerm.setLevel(0.0);

                    List<OptionPayoffs.OptionPayoffElement> protectionPayoffList = new ArrayList<>();
                    protectionPayoffList.add(payoff);

                    this.optionTermList.add(protectionTerm);
                    this.payoffsByTerm.put(OptionTermType.PROTECTION, protectionPayoffList);
                }
            }
            this.optionTermList.add(newAccumulateTerm);


            Schedule schedule = new Schedule(startDate, endDate, new Period(Frequency.Daily), rds.getCalendars().getCalendar(this.optionUnderlyingList.get(0).getCalendarName()),
                    BusinessDayConvention.Following, BusinessDayConvention.Following, DateGeneration.Rule.Forward, false);

            this.position = schedule.dates().size() * this.unitNumbers;
        }
    }
}
