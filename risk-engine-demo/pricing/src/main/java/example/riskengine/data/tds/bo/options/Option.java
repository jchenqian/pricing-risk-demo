package example.riskengine.data.tds.bo.options;


import example.riskengine.common.bases.cashflow.CashFlow;
import example.riskengine.common.bases.daycounters.DayCounter;
import example.riskengine.common.bases.time.BusinessDayConvention;
import example.riskengine.common.bases.time.Calendar;
import example.riskengine.common.bases.time.QLDate;
import example.riskengine.common.bases.time.Schedule;
import example.riskengine.common.enums.tds.*;
import example.riskengine.data.tds.bo.Instrument;

import java.util.List;

/**
 * @author qianchen
 * @date 2024/2/6
 * @description
 */
public class Option extends Instrument {

    private String baseCurve;
    private String simuCurve;
    private String volSurface;
    private String baseCurrency;
    private Double spotPrice;
    private QLDate businessDate;
    private double notional;
    private ExerciseType exerciseType;
    private OptionType paymentType;
    private Calendar calendar;
    private double strike;
    private boolean callputFlag;
    private boolean buysellFlag;

    /**
     * data member required to override pure virtual function, not all useful in this class
     */
    List<String> riskFactors;
    private String baseRatingCurveName;
    private String discountCurveName;
    BusinessDayConvention amountConvention;
    Schedule amountSchedule;
    List<CashFlow> priorPositionCashflows;
    private VolatilityType volatilityType;
    private Double vol, volBase;
    private DayCounter dayCounter;
    private Double yearFrac = 245.0;
    private String portfolioId;

    public Option() {

    }

    @Override
    public String getPortfolioId() {
        return portfolioId;
    }

    @Override
    public void setPortfolioId(String portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Option(InstrumentType instType, String instId, QLDate maturityDate,
                  QLDate unadjMaturityDate, QLDate tradeDate, QLDate unadjTradeDate,
                  double tradeRate, String currency, String department, String counterparty,
                  UserSelectedModelType userSelectedModelType, ValuationModelType valModelType,
                  double faceAmount, QLDate firstDate, QLDate unadjFirstDate, double spread,
                  QLDate priorBusinessDate, double priorPosition, String baseCurve, String simuCurve,
                  String volSurface, String baseCurrency, Double spotPrice, QLDate businessDate,
                  double notional, ExerciseType exerciseType, OptionType paymentType,
                  Calendar calendar, double strike, boolean callputFlag, boolean buysellFlag, String bizType,
                  VolatilityType volType, Double yearfrac, Double volBase, Double vol, DayCounter dayCounter, String portfolioId) {
        super(instType, instId, maturityDate, unadjMaturityDate, tradeDate, unadjTradeDate,
                tradeRate, currency, department, counterparty, userSelectedModelType,
                valModelType, faceAmount, firstDate, unadjFirstDate, spread, priorBusinessDate,
                priorPosition);
        this.baseCurve = baseCurve;
        this.simuCurve = simuCurve;
        this.volSurface = volSurface;
        this.baseCurrency = baseCurrency;
        this.spotPrice = spotPrice;
        this.businessDate = businessDate;
        this.notional = notional;
        this.exerciseType = exerciseType;
        this.paymentType = paymentType;
        this.calendar = calendar;
        this.strike = strike;
        this.callputFlag = callputFlag;
        this.buysellFlag = buysellFlag;
        this.businessType = bizType;
        this.volatilityType = volType;
        this.vol = vol;
        this.volBase = volBase;
        this.dayCounter = dayCounter;
        this.portfolioId = portfolioId;
        if (null != yearfrac) {
            this.yearFrac = yearfrac;
        }
    }

    public String getBaseCurve() {
        return baseCurve;
    }

    public void setBaseCurve(String baseCurve) {
        this.baseCurve = baseCurve;
    }

    public String getSimuCurve() {
        return simuCurve;
    }

    public void setSimuCurve(String simuCurve) {
        this.simuCurve = simuCurve;
    }

    public String getVolSurface() {
        return volSurface;
    }

    public void setVolSurface(String volSurface) {
        this.volSurface = volSurface;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public double getSpotPrice() {
        return spotPrice;
    }

    public void setSpotPrice(double spotPrice) {
        this.spotPrice = spotPrice;
    }

    public QLDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(QLDate businessDate) {
        this.businessDate = businessDate;
    }

    public double getNotional() {
        return notional;
    }

    public void setNotional(double notional) {
        this.notional = notional;
    }

    public ExerciseType getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(ExerciseType exerciseType) {
        this.exerciseType = exerciseType;
    }

    public OptionType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(OptionType paymentType) {
        this.paymentType = paymentType;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
    }

    public double getStrike() {
        return strike;
    }

    public void setStrike(double strike) {
        this.strike = strike;
    }

    public boolean isCallputFlag() {
        return callputFlag;
    }

    public void setCallputFlag(boolean callputFlag) {
        this.callputFlag = callputFlag;
    }

    public boolean isBuysellFlag() {
        return buysellFlag;
    }

    public void setBuysellFlag(boolean buysellFlag) {
        this.buysellFlag = buysellFlag;
    }

    public List<String> getRiskFactors() {
        return riskFactors;
    }

    public void setRiskFactors(List<String> riskFactors) {
        this.riskFactors = riskFactors;
    }

    public String getBaseRatingCurveName() {
        return baseRatingCurveName;
    }

    public void setBaseRatingCurveName(String baseRatingCurveName) {
        this.baseRatingCurveName = baseRatingCurveName;
    }

    public String getDiscountCurveName() {
        return discountCurveName;
    }

    public void setDiscountCurveName(String discountCurveName) {
        this.discountCurveName = discountCurveName;
    }

    public void setAmountConvention(BusinessDayConvention amountConvention) {
        this.amountConvention = amountConvention;
    }

    public void setAmountSchedule(Schedule amountSchedule) {
        this.amountSchedule = amountSchedule;
    }

    public List<CashFlow> getPriorPositionCashflows() {
        return priorPositionCashflows;
    }

    public void setPriorPositionCashflows(List<CashFlow> priorPositionCashflows) {
        this.priorPositionCashflows = priorPositionCashflows;
    }

    public VolatilityType getVolatilityType() {
        return volatilityType;
    }

    public void setVolatilityType(VolatilityType volatilityType) {
        this.volatilityType = volatilityType;
    }

    public Double getYearFrac() {
        return yearFrac;
    }

    public void setYearFrac(Double yearFrac) {
        this.yearFrac = yearFrac;
    }

    public Double getVol() {
        return vol;
    }

    public void setVol(Double vol) {
        this.vol = vol;
    }

    public Double getVolBase() {
        return volBase;
    }

    public void setVolBase(Double volBase) {
        this.volBase = volBase;
    }

    public DayCounter getDayCounter() {
        return dayCounter;
    }

    public void setDayCounter(DayCounter dayCounter) {
        this.dayCounter = dayCounter;
    }

    @Override
    public BusinessDayConvention getAmountConvention() {
        return null;
    }

    @Override
    public Schedule getAmountSchedule() {
        return null;
    }
}
