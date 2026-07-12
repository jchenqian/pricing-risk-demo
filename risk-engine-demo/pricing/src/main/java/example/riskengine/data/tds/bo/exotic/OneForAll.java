package example.riskengine.data.tds.bo.exotic;

import example.riskengine.common.bases.daycounters.DayCounter;
import example.riskengine.common.bases.time.*;
import example.riskengine.common.bases.time.Calendar;
import example.riskengine.common.enums.tds.*;
import example.riskengine.common.exceptions.EngineCodeEnums;
import example.riskengine.common.exceptions.EngineException;
import example.riskengine.data.rds.Rds;
import example.riskengine.data.tds.bo.Instrument;
import example.riskengine.data.tds.bo.repayments.RepaymentCashflow;
import example.riskengine.data.tds.bo.repayments.RepaymentCashflows;
import example.riskengine.data.tds.entity.ValIntTrdTradeExotic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author qianchen
 * @date 2023/6/6
 * @description 场外期权自定义模型实体化对象
*/
public class OneForAll extends Instrument {
    /**
     * 名义本金
     */
    private Double notional;

    /**
     * 期权费
     */
    private Double premium;

    /**
     * 期权费计提存续时间
     */
    private Double accrualDays;

    /**
     * 观察开始日、结束日
     */
    private QLDate startDate, endDate;

    /**
     * 买入卖出方向
     */
    private Boolean isBuy;

    /**
     * 看涨/看跌
     */
    private Boolean isCall;

    /**
     * 多标的观察方向
     */
    private String multiType;

    /**
     * 折现曲线
     */
    private String discountCurve;

    /**
     * 计提期权费
     */
    private Double accrualPremium = 0.0;

    /**
     * 保证金
     */
    private Double margin;

    /**
     * 计息调整日期
     */
    private int dayCountAdj;

    /**
     * 单位累积数量
     */
    private Double unitNumber = 0.0;

    /**
     * 最大累积数量
     */
    private Double maxAccuUnits = Double.POSITIVE_INFINITY;

    /**
     * 交易能否提前终止
     */
    private Boolean terminateFlag = true, returnFlag;

    /**
     * 折现利率
     */
    private Double discountRate = 0.0;

    /**
     * 累积期权标识
     */
    private Boolean isAccuOption;

    /**
     * 累积期权结算频率
     */
    private Frequency settlementFrequency;

    /**
     * 累积期权到期/Daily翻倍
     */
    private Frequency paymentMultiplierFrequency;
    private List<OptionTerms.OptionTermElement> optionTermList;
    private List<OptionPayoffs.OptionPayoffElement> optionPayoffList;
    private List<OptionUnderlyings.OptionUnderlyingElement> optionUnderlyingList;
    private RepaymentCashflows observationDates;
    
    /**
     * OptionTermType => 条款类型, OptionObsByTerm => 单一条款对应的观察日
     */
    private Map<OptionTermType, OptionObsByTerm> obsByTerm = new ConcurrentHashMap<>();

    /**
     * 亚式期权增强价格
     */
    private Double enhancedPrice = 0.0;
    private Map<OptionTermType, List<OptionPayoffs.OptionPayoffElement>> payoffsByTerm = new ConcurrentHashMap<>();
    private DayCounter dayCounter;
    private Calendar calendar;
    private ExerciseType exerciseType;
    private AverageRule averageRule;

    /**
     * 跑自定义Python模型的python文件名
     * 取自 businessType为B010101的 vValueModel字段
     */
    private String pythonPath;

    private OptionReferencesWali optionReferencesWali;

    public OneForAll(ValIntTrdTradeExotic valIntTrdTradeExotic, OptionTerms terms,
                     OptionUnderlyings underlyings, OptionPayoffs payoffs,
                     RepaymentCashflows observationDates, Rds rds
    ) {
        super(InstrumentType.EXOTIC,
                valIntTrdTradeExotic.getvTradeNo(),
                new QLDate(valIntTrdTradeExotic.getdOptionMaturityDate()),
                new QLDate(valIntTrdTradeExotic.getdOptionMaturityDate()),
                new QLDate(valIntTrdTradeExotic.getdTradeDt()),
                new QLDate(valIntTrdTradeExotic.getdTradeDt()),
                0.0,
                valIntTrdTradeExotic.getvCurrencyCode(),
                "",
                "",
                UserSelectedModelType.valueOf(valIntTrdTradeExotic.getvValueModel()),
                ValuationModelType.OPTION_BARRIER_VALMODEL,
                0.0,
                new QLDate(),
                new QLDate(),
                0.0,
                new QLDate(),
                valIntTrdTradeExotic.getnMultiplier() * valIntTrdTradeExotic.getnContractPosition());

        this.portfolioId = valIntTrdTradeExotic.getvPortfolioId();
        this.calendar = rds.getCalendars().getCalendar(valIntTrdTradeExotic.getvCalendar());
        this.businessType = valIntTrdTradeExotic.getvBusinessType();
        this.dayCounter = DayCounter.valueOf(valIntTrdTradeExotic.getvDaycountBasis(), this.calendar);
        this.notional = valIntTrdTradeExotic.getnNotional();
        this.isBuy = "B".equals(valIntTrdTradeExotic.getvBuysellFlag());
        this.isCall = "C".equals(valIntTrdTradeExotic.getvCallputFlag());
        this.multiType = valIntTrdTradeExotic.getvMultiunderlyingType();
        this.startDate = new QLDate(valIntTrdTradeExotic.getdStartDate());
        this.endDate = new QLDate(valIntTrdTradeExotic.getdEndDate());
        this.discountCurve = valIntTrdTradeExotic.getvDiscountCurve();
        this.terminateFlag = !"0".equals(valIntTrdTradeExotic.getvEndFlag());
        this.returnFlag = "1".equals(valIntTrdTradeExotic.getvReturnFlag());
        this.dayCountAdj = 0;

        this.unitNumber = valIntTrdTradeExotic.getnUnitNumer();
        this.isAccuOption = "1".equals(valIntTrdTradeExotic.getvAccuFlag());
        this.settlementFrequency = Frequency.valueOf(valIntTrdTradeExotic.getvCfFlag());
        this.paymentMultiplierFrequency = Frequency.valueOf(valIntTrdTradeExotic.getvAccFlag());
        this.exerciseType = ExerciseType.valueOf(valIntTrdTradeExotic.getvExerciseType());
        this.averageRule = AverageRule.valueOf(valIntTrdTradeExotic.getvAsianType());
        this.enhancedPrice = valIntTrdTradeExotic.getnEnhance();

        // 当前版本无此逻辑
        this.margin = 0.0;
        this.accrualPremium = 0.0;


        if (terms.getOptionTermList().size() == 0) {
            throw new EngineException(EngineCodeEnums.ERROR_THISTRADE_NO_TERMS, instId);
        }
        if (payoffs.getOptionPayoffList().size() == 0 ) {
            throw new EngineException(EngineCodeEnums.ERROR_THISTRADE_NO_PAYOFFS, instId);
        }
        if (underlyings.getOptionUnderlyingElementList().size() == 0) {
            throw new EngineException(EngineCodeEnums.ERROR_THISTRADE_NO_UNDERLYINGS, instId);
        }
        if (observationDates.getCashflows().size() == 0) {
            // 对于obs为空的情况, 在Engine中初始化
            logger.info("该笔期权没有观察日数据，使用固定频率生成" + instId);
        } else {
            this.observationDates = observationDates;
            sortObservationDates();
        }
        this.optionTermList = terms.getOptionTermList();
        this.optionPayoffList = payoffs.getOptionPayoffList();
        for (OptionPayoffs.OptionPayoffElement temp : optionPayoffList) {
            OptionTermType term = temp.getTerm();
            payoffs.addOptionPayoffByTerm(term, temp);
        }
        this.payoffsByTerm = payoffs.getOptionPayoffsMap();
        this.optionUnderlyingList = underlyings.getOptionUnderlyingElementList();
        sortElements();

        /**
         * OptionReferences ->
         */

    }

    /**
     * 针对自定义 python模型的产品类型，走自定义Python调用引擎，不走oneForAll引擎
     * @param valIntTrdTradeExotic
     * @param terms
     * @param underlyings
     * @param payoffs
     * @param observationDates
     * @param rds
     * @param businessType  取自 businessType为B010101的 vValueModel字段
     */
    public OneForAll(ValIntTrdTradeExotic valIntTrdTradeExotic, OptionTerms terms,
                     OptionUnderlyings underlyings, OptionPayoffs payoffs,
                     RepaymentCashflows observationDates, Rds rds,String businessType
    ) {
        //针对自定义 python模型的产品类型，走自定义Python调用引擎，不走oneForAll引擎
        super(InstrumentType.EXOTIC,
                valIntTrdTradeExotic.getvTradeNo(),
                new QLDate(valIntTrdTradeExotic.getdOptionMaturityDate()),
                new QLDate(valIntTrdTradeExotic.getdOptionMaturityDate()),
                new QLDate(valIntTrdTradeExotic.getdTradeDt()),
                new QLDate(valIntTrdTradeExotic.getdTradeDt()),
                0.0,
                valIntTrdTradeExotic.getvCurrencyCode(),
                "",
                "",
                UserSelectedModelType.MONTE_CARLO_USERMODEL,
                ValuationModelType.OPTION_BARRIER_VALMODEL,
                0.0,
                new QLDate(),
                new QLDate(),
                0.0,
                new QLDate(),
                valIntTrdTradeExotic.getnMultiplier() * valIntTrdTradeExotic.getnContractPosition());

        this.portfolioId = valIntTrdTradeExotic.getvPortfolioId();
        this.calendar = rds.getCalendars().getCalendar(valIntTrdTradeExotic.getvCalendar());
        this.businessType = valIntTrdTradeExotic.getvBusinessType();
        this.dayCounter = DayCounter.valueOf(valIntTrdTradeExotic.getvDaycountBasis(), this.calendar);
        this.notional = valIntTrdTradeExotic.getnNotional();
        this.isBuy = "B".equals(valIntTrdTradeExotic.getvBuysellFlag());
        this.isCall = "C".equals(valIntTrdTradeExotic.getvCallputFlag());
        this.multiType = valIntTrdTradeExotic.getvMultiunderlyingType();
        this.startDate = new QLDate(valIntTrdTradeExotic.getdStartDate());
        this.endDate = new QLDate(valIntTrdTradeExotic.getdEndDate());
        this.discountCurve = valIntTrdTradeExotic.getvDiscountCurve();
        this.terminateFlag = !"0".equals(valIntTrdTradeExotic.getvEndFlag());
        this.returnFlag = "1".equals(valIntTrdTradeExotic.getvReturnFlag());
        this.dayCountAdj = 0;

        this.unitNumber = valIntTrdTradeExotic.getnUnitNumer();
        this.isAccuOption = "1".equals(valIntTrdTradeExotic.getvAccuFlag());
        this.settlementFrequency = Frequency.valueOf(valIntTrdTradeExotic.getvCfFlag());
        this.paymentMultiplierFrequency = Frequency.valueOf(valIntTrdTradeExotic.getvAccFlag());

        // 当前版本无此逻辑
        this.margin = 0.0;
        this.accrualPremium = 0.0;

        //自定义Python模型 Python文件名称
        this.pythonPath  = valIntTrdTradeExotic.getvValueModel();


        /*if (terms.getOptionTermList().size() == 0) {
            throw new EngineException(EngineCodeEnums.ERROR_THISTRADE_NO_TERMS, instId);
        }
        if (payoffs.getOptionPayoffList().size() == 0 ) {
            throw new EngineException(EngineCodeEnums.ERROR_THISTRADE_NO_PAYOFFS, instId);
        }
        if (underlyings.getOptionUnderlyingElementList().size() == 0) {
            throw new EngineException(EngineCodeEnums.ERROR_THISTRADE_NO_UNDERLYINGS, instId);
        }*/
        if (observationDates.getCashflows().size() == 0) {
            // 对于obs为空的情况, 在Engine中初始化
            logger.info("该笔期权没有观察日数据，使用固定频率生成" + instId);
        } else {
            this.observationDates = observationDates;
            sortObservationDates();
        }
        this.optionTermList = terms.getOptionTermList();
        this.optionPayoffList = payoffs.getOptionPayoffList();
        for (OptionPayoffs.OptionPayoffElement temp : optionPayoffList) {
            OptionTermType term = temp.getTerm();
            payoffs.addOptionPayoffByTerm(term, temp);
        }
        this.payoffsByTerm = payoffs.getOptionPayoffsMap();
        this.optionUnderlyingList = underlyings.getOptionUnderlyingElementList();
        sortElements();

    }

    /**
     * @author qianchen
     * @date 2023/6/8
     * @description 根据条款优先级顺序对条款和条款对应payoff进行排序
     * @note payoffs.get(index)为terms.get(index)对应的条款
     * @note payoffs.size() = terms.size() + 1, 最后一项为default条款
    */
    private void sortElements() {
        optionTermList.sort(Comparator.comparing(OptionTerms.OptionTermElement::getOrder));
        logger.debug(this.instId + optionTermList.toString());

        optionPayoffList.sort(Comparator.comparing(OptionPayoffs.OptionPayoffElement::getTermOrder));
        logger.debug(this.instId + optionPayoffList.toString());
    }

    /**
     * @author qianchen
     * @date 2023/6/15
     * @description 按照条款类型对观察日进行封装
    */
    private void sortObservationDates() {
        for (RepaymentCashflow temp : this.observationDates.getCashflows()) {
            OptionTermType type = temp.getOptionTerm();
            OptionObsByTerm obs = this.getObsByTerms(type);
            // 默认读进来是按照日期顺序的
            QLDate date = temp.getPaydate();
            double level = temp.getLevel();
            double term = temp.getAmount();
            double rate = temp.getTerm();
            obs.getObsDates().add(date);
            obs.getObsLevel().add(level);
            obs.getObsTerm().add(term);
            obs.getRebateRates().add(rate);
            // 重新放回Map中，以条款类型为key
            this.obsByTerm.put(type, obs);
        }
    }


    /**
     * @author qianchen
     * @date 2023/6/15
     * @description 条款对应观察日子类，存储观察日期、观察水平、年化时间
    */
    public static class OptionObsByTerm {
        public OptionObsByTerm() {}
        private List<QLDate>obsDates = new ArrayList<>();
        private List<Double>obsTerm = new ArrayList<>();
        private List<Double>obsLevel = new ArrayList<>();
        private List<Double>rebateRates = new ArrayList<>();

        public List<QLDate> getObsDates() {
            return obsDates;
        }

        public void setObsDates(List<QLDate> obsDates) {
            this.obsDates = obsDates;
        }

        public List<Double> getObsTerm() {
            return obsTerm;
        }

        public void setObsTerm(List<Double> obsTerm) {
            this.obsTerm = obsTerm;
        }

        public List<Double> getObsLevel() {
            return obsLevel;
        }

        public void setObsLevel(List<Double> obsLevel) {
            this.obsLevel = obsLevel;
        }

        public List<Double> getRebateRates() {
            return rebateRates;
        }

        public void setRebateRates(List<Double> rebateRates) {
            this.rebateRates = rebateRates;
        }
    }

    public static class OptionPayoffByTerm {
        public OptionPayoffByTerm() {}
        private List<OptionPayoffs> payoffsOneTerm = new ArrayList<>();
    }


    private OptionObsByTerm getObsByTerms(OptionTermType type) {
        if (!this.obsByTerm.containsKey(type)) {
            OptionObsByTerm obs = new OptionObsByTerm();
            this.obsByTerm.put(type, obs);
        }
        return this.obsByTerm.get(type);
    }


    @Override
    public BusinessDayConvention getAmountConvention() {
        return null;
    }

    @Override
    public Schedule getAmountSchedule() {
        return null;
    }

    public Double getNotional() {
        return notional;
    }

    public void setNotional(Double notional) {
        this.notional = notional;
    }

    public Double getPremium() {
        return premium;
    }

    public void setPremium(Double premium) {
        this.premium = premium;
    }

    public Double getAccrualDays() {
        return accrualDays;
    }

    public void setAccrualDays(Double accrualDays) {
        this.accrualDays = accrualDays;
    }

    public QLDate getStartDate() {
        return startDate;
    }

    public void setStartDate(QLDate startDate) {
        this.startDate = startDate;
    }

    public QLDate getEndDate() {
        return endDate;
    }

    public void setEndDate(QLDate endDate) {
        this.endDate = endDate;
    }

    public Boolean getBuy() {
        return isBuy;
    }

    public void setBuy(Boolean buy) {
        isBuy = buy;
    }

    public String getMultiType() {
        return multiType;
    }

    public void setMultiType(String multiType) {
        this.multiType = multiType;
    }

    public List<OptionTerms.OptionTermElement> getOptionTermList() {
        return optionTermList;
    }

    public void setOptionTermList(List<OptionTerms.OptionTermElement> optionTermList) {
        this.optionTermList = optionTermList;
    }

    public List<OptionPayoffs.OptionPayoffElement> getOptionPayoffList() {
        return optionPayoffList;
    }

    public void setOptionPayoffList(List<OptionPayoffs.OptionPayoffElement> optionPayoffList) {
        this.optionPayoffList = optionPayoffList;
    }

    public List<OptionUnderlyings.OptionUnderlyingElement> getOptionUnderlyingList() {
        return optionUnderlyingList;
    }

    public void setOptionUnderlyingList(List<OptionUnderlyings.OptionUnderlyingElement> optionUnderlyingList) {
        this.optionUnderlyingList = optionUnderlyingList;
    }

    public RepaymentCashflows getObservationDates() {
        return observationDates;
    }

    public void setObservationDates(RepaymentCashflows observationDates) {
        this.observationDates = observationDates;
    }

    public Map<OptionTermType, OptionObsByTerm> getObsByTerm() {
        return obsByTerm;
    }

    public void setObsByTerm(Map<OptionTermType, OptionObsByTerm> obsByTerm) {
        this.obsByTerm = obsByTerm;
    }

    public Boolean getCall() {
        return isCall;
    }

    public void setCall(Boolean call) {
        isCall = call;
    }

    public String getDiscountCurve() {
        return discountCurve;
    }

    public void setDiscountCurve(String discountCurve) {
        this.discountCurve = discountCurve;
    }

    public Double getAccrualPremium() {
        return accrualPremium;
    }

    public void setAccrualPremium(Double accrualPremium) {
        this.accrualPremium = accrualPremium;
    }

    public Map<OptionTermType, List<OptionPayoffs.OptionPayoffElement>> getPayoffsByTerm() {
        return payoffsByTerm;
    }

    public void setPayoffsByTerm(Map<OptionTermType, List<OptionPayoffs.OptionPayoffElement>> payoffsByTerm) {
        this.payoffsByTerm = payoffsByTerm;
    }

    public Double getMargin() {
        return margin;
    }

    public void setMargin(Double margin) {
        this.margin = margin;
    }

    public int getDayCountAdj() {
        return dayCountAdj;
    }

    public void setDayCountAdj(int dayCountAdj) {
        this.dayCountAdj = dayCountAdj;
    }

    public Double getUnitNumber() {
        return unitNumber;
    }

    public void setUnitNumber(Double unitNumber) {
        this.unitNumber = unitNumber;
    }

    public Double getMaxAccuUnits() {
        return maxAccuUnits;
    }

    public void setMaxAccuUnits(Double maxAccuUnits) {
        this.maxAccuUnits = maxAccuUnits;
    }

    public Boolean getTerminateFlag() {
        return terminateFlag;
    }

    public void setTerminateFlag(Boolean terminateFlag) {
        this.terminateFlag = terminateFlag;
    }

    public Double getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(Double discountRate) {
        this.discountRate = discountRate;
    }

    public DayCounter getDayCounter() {
        return dayCounter;
    }

    public void setDayCounter(DayCounter dayCounter) {
        this.dayCounter = dayCounter;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
    }

    public Boolean getReturnFlag() {
        return returnFlag;
    }

    public void setReturnFlag(Boolean returnFlag) {
        this.returnFlag = returnFlag;
    }

    public Boolean getAccuOption() {
        return isAccuOption;
    }

    public void setAccuOption(Boolean accuOption) {
        isAccuOption = accuOption;
    }

    public Frequency getSettlementFrequency() {
        return settlementFrequency;
    }

    public void setSettlementFrequency(Frequency settlementFrequency) {
        this.settlementFrequency = settlementFrequency;
    }

    public Frequency getPaymentMultiplierFrequency() {
        return paymentMultiplierFrequency;
    }

    public void setPaymentMultiplierFrequency(Frequency paymentMultiplierFrequency) {
        this.paymentMultiplierFrequency = paymentMultiplierFrequency;
    }

    public String getPythonPath() {
        return pythonPath;
    }

    public void setPythonPath(String pythonPath) {
        this.pythonPath = pythonPath;
    }

    public ExerciseType getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(ExerciseType exerciseType) {
        this.exerciseType = exerciseType;
    }

    public AverageRule getAverageRule() {
        return averageRule;
    }

    public void setAverageRule(AverageRule averageRule) {
        this.averageRule = averageRule;
    }

    public Double getEnhancedPrice() {
        return enhancedPrice;
    }

    public void setEnhancedPrice(Double enhancedPrice) {
        this.enhancedPrice = enhancedPrice;
    }

    public OptionReferencesWali getOptionReferencesWali() {
        return optionReferencesWali;
    }

    public void setOptionReferencesWali(OptionReferencesWali optionReferencesWali) {
        this.optionReferencesWali = optionReferencesWali;
    }
}
