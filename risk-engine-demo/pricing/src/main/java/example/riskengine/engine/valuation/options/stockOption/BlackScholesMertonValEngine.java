package example.riskengine.engine.valuation.options.stockOption;

import example.riskengine.common.bases.math.matrixutilities.Array;
import example.riskengine.common.bases.termstructures.surfaces.Surface;
import example.riskengine.common.bases.termstructures.yieldcurves.ZeroCurve;
import example.riskengine.common.bases.time.BusinessDayConvention;
import example.riskengine.common.bases.time.Calendar;
import example.riskengine.common.bases.time.QLDate;
import example.riskengine.common.bases.tools.HistoricalVol;
import example.riskengine.common.enums.mds.PriceType;
import example.riskengine.common.enums.mds.SurfaceType;
import example.riskengine.common.enums.rds.IndicatorType;
import example.riskengine.common.enums.tds.VolatilityType;
import example.riskengine.common.exceptions.EngineCodeEnums;
import example.riskengine.common.exceptions.EngineException;
import example.riskengine.data.mds.bo.price.Price;
import example.riskengine.data.tds.bo.options.StockOption.EuStockOption;
import example.riskengine.engine.valuation.BaseOptionValEngine;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author qianchen
 * @date 2022/11/25
 * @description 单笔欧式期权计算引擎
 */
@Component("STOCKOPTION_OPTION_EU_VALMODEL_BLACK_SCHOLES_USERMODEL")
@Scope("prototype")
public class BlackScholesMertonValEngine extends BaseOptionValEngine {
    private final DecimalFormat format = new DecimalFormat("######0.00000000");
    private EuStockOption option;
    private Double vol;  // volatility: 波动率
    private Double r;  // risk-free interest rate: 无风险利率曲线利率
    private Double q;  // dividends: 平均股息率
    private Double t;  // time to maturity: 当前日期至到期日的时间(剩余期限)
    private Double strike;  // strike price: 期权执行价格
    private Double S0;  // spot price: 当前即期价格
    private Double spotPrice;  // spot price: 期初价格
    private Double notional;  // nominal: 名义本金
    //    private Double discountFactor;
    private QLDate valDate, maturityDate;
    private Calendar calendar;
    private ZeroCurve baseCurve;
    private Surface surface;
    private double dayNumber, participateRate;
    private final static double bp = 0.0001;
    private double pv;
    private double yearFrac;
    private double position;


    @Override
    protected void prepare() {
        option = (EuStockOption) this.instrument;
        position = option.getPriorPosition();
        valDate = new QLDate(this.cacheCfg.getValDate());
        calendar = super.rds.getCalendars().getCalendar(option.getCalendarName());
        maturityDate = calendar.adjust(option.getMaturityDate(), BusinessDayConvention.Following);
        t = option.getDayCounter().yearFraction(qlValDate, maturityDate); // 使用交易日算惯例
        yearFrac = option.getYearFrac();

        /*估值日股票价格*/
        String stockCode = option.getSecurityCode();
        PriceType underlyingType = option.getAssetType();
        Price stockPrice = super.mds.getTypePrices(underlyingType, stockCode);
        S0 = stockPrice.getPriceByDate(this.qlValDate);
        logger.debug("交易号:"+this.instrument.getInstId()+" 标的资产股票/股指/ETF代码:{}, 标的资产股票估值日价格:{}", stockCode, S0);

        this.vol = calculateOptionVolatility();

        // 获得标的股息率，优先客制化输入
        try {
            if (option.getDividend() != null) {
                this.q = option.getDividend();
            } else {
                this.q = super.mds.getTypePriceByDay(PriceType.DIVIDEND, stockCode, qlValDate);
                logger.debug("交易号:"+this.instrument.getInstId()+" 标的资产股票分红派息:" + q);
            }
        } catch (Exception e) {
            this.q = 0.0;
            logger.warn("交易号:"+this.instrument.getInstId()+"未找到标的资产股票分红派息数据, 使用0.0计算! ! !");
        }


        // 获得无风险利率，优先客制化输入
        if (option.getRfr() != null) {
            this.r = option.getRfr();
        } else {
            String discountCurveName = option.getBaseCurve();
            baseCurve = super.mds.getIrCurve(discountCurveName).getOneDateCurve(qlValDate);
            double dfTerm = baseCurve.dayCounter().yearFraction(qlValDate, maturityDate); // 使用曲线日算惯例
            r = baseCurve.getZeroYield(dfTerm);
//            discountFactor = discountFactor(this.r, dfTerm);
        }
        logger.debug("交易号:"+this.instrument.getInstId()+" 无风险利率:" + r);

        // 获取交易数据
        strike = option.getStrike();
        participateRate = option.getParticipateRate();
    }


    /**
     * @author snow.li
     * @date 2022/2/16
     * @description 确定求解些量
     */
    @Override
    protected Double calc(IndicatorType indicatorType) {
        Double result = null;
        switch (indicatorType) {
            case PV:
                result = calculatePv(this.r);
                break;
            case DIVIDEND:
                result = this.q;
                break;
            case VOLATILITY:
                result = vol;
                break;
            case VOLATILITYDAYNUMBER:
                result = dayNumber;
                break;
            case RFR:
                result = r;
                break;
            case D1:
                result = BSD1();
                break;
            case D2:
                result = BSD2();
                break;
            case SPOTPRICE:
                result = S0;
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
                result = calculateVega() * this.vol * position;
                break;
            case THETA:
                result = calculateTheta();
                break;
            case THETA_AMOUNT:
                result = calculateTheta() * this.t * position;
                break;
            case RHO:
                result = calculateRho();
                break;
            case DV01:
                result = calculateDV01();
                break;
            case HIS_VOL:
                result = calculateVolatilityForVegaCapital();
                break;
            default:
                break;
        }
        return result;
    }

    private Double calculateDV01() {
        double result = calculatePv(this.r + bp) - calculatePv(this.r - bp);
        return result / 2;
    }

    private Double calculateRho() {
        double result = option.isCallputFlag() ? strike * t * discountFactor(r, t) * cdf(BSD2())
                : -strike * t * discountFactor(r, t) * (1 - cdf(BSD2()));
        logger.debug("交易号:"+this.instrument.getInstId()+"BSM期权Rho = " + result);
        if (!option.isBuysellFlag()) {
            result *= -1;
        }
        return result * this.participateRate;
    }

    private Double calculateTheta() {
        double a = -0.5 * discountFactor(q, t) * S0 * pdf(BSD1()) * vol / Math.sqrt(t);
        double b = this.r * strike * discountFactor(this.r, t) * cdf(BSD2());
        double c = this.q * S0 * discountFactor(q, t) *  cdf(BSD1());
        double d = this.r * strike * discountFactor(this.r, t) * cdf(-BSD2());
        double f = this.q * S0 * discountFactor(q, t) *  cdf(-BSD1());
        double result = option.isCallputFlag() ? a - b + c : a + d - f;
        logger.debug("交易号:"+this.instrument.getInstId()+"BSM期权Theta = " + result);
        if (!option.isBuysellFlag()) {
            result *= -1;
        }
        return result * this.participateRate;
    }

    private Double calculateVega() {
        double result = S0 * discountFactor(q, t) * pdf(BSD1()) * Math.sqrt(t);
        logger.debug("交易号:"+this.instrument.getInstId()+"BSM期权Vega = " + result);
        if (!option.isBuysellFlag()) {
            result *= -1;
        }
        return result * this.participateRate;
    }

    private Double calculateGammaAmount(Boolean isAdjusted) {
        double gamma = calculateGamma();

        if (!isAdjusted) {
            return position * gamma * Math.pow(S0, 2) / 100;
        } else {
            return 0.5 * gamma * Math.pow(S0 * vol / Math.sqrt(yearFrac), 2);
        }

    }

    private Double calculateGamma() {
        double p = pdf(BSD1());
        double result = discountFactor(q, t) * p / (S0 * vol * Math.sqrt(t));
        logger.debug("交易号:"+this.instrument.getInstId()+"BSM期权Gamma = " + result);
        if (!option.isBuysellFlag()) {
            result *= -1;
        }
        return result * this.participateRate;
    }

    private Double calculateDeltaAmount(Boolean isAdjusted) {
        double delta = calculateDelta();

        if (!isAdjusted) {
            return delta * S0 * position;
        } else {
            return delta * S0 * position * this.vol / Math.sqrt(yearFrac);
        }
    }

    private Double calculateDelta() {
        double d1 = BSD1();
        double result = option.isCallputFlag() ? discountFactor(q, t) * cdf(d1) : -discountFactor(q, t) * (1 - cdf(d1));
        logger.debug("交易号:"+this.instrument.getInstId()+"BSM期权Delta = " + result);
        if (!option.isBuysellFlag()) {
            result *= -1;
        }
        return result * this.participateRate;
    }


    /**
     * @author snow.li
     * @date 2022/2/16
     * @description 求解过程
     */
    // 求解BS模型的d1
    private double BSD1(Double discountRate) {
        double a = 0, b = 0, c = 0, d1 = 0;
        a = Math.log(S0 / strike);
        b = (discountRate - q + 0.5 * Math.pow(vol, 2)) * t;
        c = vol * Math.sqrt(t);
        d1 = (a + b) / c;
        logger.debug("交易号:"+this.instrument.getInstId()+" D1:" + d1);
        return d1;
    }

    private double BSD1() {
        return BSD1(this.r);
    }


    // 求解BS模型的d2
    private double BSD2(Double discountRate) {
        double a = 0, b = 0, c = 0, d2 = 0;
        a = Math.log(S0 / strike);
        b = (discountRate - q - 0.5 * Math.pow(vol, 2)) * t;
        c = vol * Math.sqrt(t);
        d2 = (a + b) / c;
        logger.debug("交易号:"+this.instrument.getInstId()+" D2:" + d2);
        return d2;
    }

    private double BSD2() {
        return BSD2(this.r);
    }


    // BS模型计算外汇期权价格过程
    private double calculatePvByBsModel(Double discountRate) {
        double pricing;
        // 赋值d1,d2
        double d1, d2;
        d1 = BSD1(discountRate);
        d2 = BSD2(discountRate);
        logger.debug("交易号:"+this.instrument.getInstId()+" D1:" + d1 + "  ---D2:" + d2);
        logger.debug("交易号:"+this.instrument.getInstId()+" N(D1):" + cdf(d1) + "  --- N(D2):" + cdf(d2));
        // 看涨看跌
        if (option.isCallputFlag()) {
            pricing = S0 * discountFactor(q, t) * cdf(d1) - strike * discountFactor(discountRate, t) * cdf(d2);
        } else {
            pricing = strike * discountFactor(discountRate, t) * (1 - cdf(d2)) - S0 * discountFactor(q, t) * (1 - cdf(d1));
        }
        // 买入卖出
        if (!option.isBuysellFlag()) {
            pricing = -pricing;
        }
        return pricing;
    }

    double calculatePv(Double discountRate) {
        /**
         * 若估值日=到期日，直接计算结算金额
         */
        if (qlValDate.eq(maturityDate))
            return option.isCallputFlag() ? Math.max(0, S0 - strike) * participateRate * position
                    : Math.max(0, strike - S0) * participateRate * position;
        double pvPerUnit = calculatePvByBsModel(discountRate);
        this.pv = pvPerUnit * participateRate * position;
        return pv;
    }

    private double calcRho(Price stockPrices, Price fxSpots) {
        double rho = 0.0;
        List<Double> stockPriceList = new ArrayList<Double>();
        List<Double> fxSpotList = new ArrayList<Double>();
        for (int i = 365; i > 0; i--) { // i=365
            QLDate d = valDate.sub(i);
            if (!calendar.isBusinessDay(d)) {
                continue;
            }
            //准备计算correlation的ArrayX：历史股价
            double stockPrice = stockPrices.getPriceByDate(d);
            stockPriceList.add(stockPrice);
            //准备计算correlation的ArrayY：历史汇率
            double spot = fxSpots.getPriceByDate(d);
            fxSpotList.add(spot);
        }
        int size = stockPriceList.size();
        //根据历史股价及对应的汇率计算correlation
        Array x = new Array(stockPriceList);
        Array y = new Array(fxSpotList);
        rho = calculateQuantoCorrelation(x, y);
        return rho;
    }

    /**
     * @author qianchen
     * @date 2024/4/1
     * @description
     */
    private Double calculateOptionVolatility() {
        VolatilityType type = option.getVolatilityType();
        logger.debug("交易号:"+this.instrument.getInstId()+"香草期权(股票、ETF){}波动率算法: {}", this.instrument.getInstId(), type);
        double result = 0.0;
        try {
            switch (type) {
                case HISTORICAL:
                    PriceType volPriceType = this.option.getAssetType().equals(PriceType.FUND) ? PriceType.FUND : option.getVolPriceType();
                    Price adjPrice = super.mds.getTypePrices(volPriceType, option.getSecurityCode());  // 计算波动率价格
                    this.dayNumber = 0.0;
                    if (option.getVolBase() != null) {
                        dayNumber = option.getVolBase();
                    } else {
                        dayNumber = maturityDate.sub(qlValDate);
//                        dayNumber = HistoricalVol.adjustDayNumber(qlValDate, dayNumber, calendar);
                    }
                    result = HistoricalVol.calculateVolatility(qlValDate, dayNumber, adjPrice, adjPrice, calendar, yearFrac);
                    break;
                case IMPLIED:
                    String surfaceId = option.getVolSurface();
                    surface = super.mds.getVolSurface(surfaceId, SurfaceType.COM).getOneDateSurface(qlValDate);
                    strike = option.getStrike();
                    double moneyness = this.strike / S0;
                    double volTerm = this.surface.getDayCounter().yearFraction(this.qlValDate, this.maturityDate);
                    result = surface.getVol(volTerm, moneyness);
                    break;
                case TRADE:
                    result = option.getVol();
                    break;
                default:
                    break;
            }
        } catch (EngineException e) {
            EngineException ne = new EngineException(e, EngineCodeEnums.ERROR_UNKNOWN_VOLATILITY, this.instrument.getInstId());
            logger.error(ne.getMessage(), e);
            logger.error("交易号:"+this.instrument.getInstId()+"tradeNo = " + this.instrument.getInstId() + " error!!!", e);
        }
        this.option.setVol(result);
        return result;
    }

    private Double calculateVolatilityForVegaCapital() {
        double vol = 0.0, days;
        PriceType volPriceType = this.option.getAssetType().equals(PriceType.FUND) ? PriceType.FUND : option.getVolPriceType();
        Price adjPrice = super.mds.getTypePrices(volPriceType, option.getSecurityCode());  // 计算波动率价格

        try {
            Calendar calendar = rds.getCalendars().getCalendar(option.getCalendarName());
            vol = HistoricalVol.calculateVolatilityforVegaCapital(qlValDate, adjPrice, calendar);
            return vol;
        } catch (EngineException e) {
            if (e.equals(new EngineException(EngineCodeEnums.ERROR_UNMATCHED_DAYNUMBER)))
                return 0.3;
        } catch (Exception ee) {
            logger.warn("交易号:"+this.instrument.getInstId()+"用于vega敏感度计量的波动率计算失败, 使用0.3兜底");
            return  0.3;
        }
        return vol;
    }

}

