package example.riskengine.engine.valuation.options.futureOption;


import example.riskengine.common.bases.termstructures.surfaces.ComSurface;
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
import example.riskengine.data.tds.bo.options.FutureOption.FutureOption;
import example.riskengine.engine.valuation.BaseOptionValEngine;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;

/**
 * @author qianchen
 * @date 2024/2/26
 * @description 期货期权(欧式)定价模型
 */
@Component("FUTUREOPTION_OPTION_EU_VALMODEL_BLACK_SCHOLES_USERMODEL")
@Scope("prototype")
public class BlackValEngine extends BaseOptionValEngine {
    private final DecimalFormat format = new DecimalFormat("######0.00000000");
    private FutureOption option;
    private Double vol;  // volatility: 波动率
    private Double r;  // risk-free interest rate: 无风险利率曲线利率
    private Double t;  // time to maturity: 当前日期至到期日的时间(剩余期限)
    private Double strike;  // strike price: 期权执行价格
    private Double F0;  // spot price: 当前即期价格
    private Double spotPrice;  // spot price: 期初价格
    private Double notional;  // nominal: 名义本金
    private Double discountFactor;
    private QLDate valDate, maturityDate;
    private Calendar calendar;
    private ZeroCurve baseCurve;
    private ComSurface surface;
    private double dayNumber, participateRate;
    private double accrualPremium;
    private final static double bp = 0.0001;
    private double pv;
    private double position, yearFrac;
    private PriceType assetType;


    @Override
    protected void prepare() {
        option = (FutureOption) this.instrument;
        position = option.getPriorPosition();
        yearFrac = option.getYearFrac();
        valDate = new QLDate(this.cacheCfg.getValDate());
        calendar = super.rds.getCalendars().getCalendar(option.getCalendarName());
        maturityDate = this.calendar.adjust(option.getMaturityDate(), BusinessDayConvention.Following);
        t = option.getDayCounter().yearFraction(qlValDate, maturityDate); // 使用交易日算惯例
        accrualPremium = 0.0;  // 当前版本不考虑计提期权费

        /*估值日股票价格*/
        String securityCode = option.getSecurityCode();
        assetType = PriceType.valueOf(option.getAssetType());
        Price fwdPrice = super.mds.getTypePrices(assetType, securityCode);
        F0 = fwdPrice.getPriceByDate(this.qlValDate);
        strike = option.getStrike();
        logger.debug("交易号:"+this.instrument.getInstId()+" 标的资产{}估值日远期价格:{}", securityCode, F0);

        // 获得标的价格波动率，优先客制化输入
        this.vol = calculateOptionVolatility();

        // 获得无风险利率，优先客制化输入
        if (option.getRfr() != null) {
            this.r = option.getRfr();
        } else {
            String discountCurveName = option.getBaseCurve();
            baseCurve = super.mds.getIrCurve(discountCurveName).getOneDateCurve(qlValDate);
            double dfTerm = baseCurve.dayCounter().yearFraction(qlValDate, maturityDate);
            r = baseCurve.getZeroYield(dfTerm);
//            discountFactor = discountFactor(r, dfTerm);
        }
        logger.debug("交易号:"+this.instrument.getInstId()+" 无风险利率:" + r);

        // 获取交易数据
        strike = option.getStrike();
        notional = option.getNotional();
        participateRate = option.getParticipateRate();
//        spotPrice = option.getSpotPrice();
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
                result = F0;
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
        // fixme
        double result;
        double d1, d2;
        d1 = BSD1();
        d2 = BSD2();
        // 看涨看跌
        if (option.isCallputFlag()) {
            result = discountFactor(this.r, t) * (F0 * cdf(d1) - strike * cdf(d2));
        } else {
            result = discountFactor(this.r, t) * (strike * (1 - cdf(d2)) - F0 * (1 - cdf(d1)));
        }
        if (!option.isBuysellFlag()) {
            result *= -1;
        }
        logger.debug("交易号:"+this.instrument.getInstId()+"Black期权Rho = " + result);
        return -t * result * this.participateRate;
    }

    private Double calculateTheta() {
        double a = -0.5 * discountFactor(r, t) * F0 * pdf(BSD1()) * vol / Math.sqrt(t);
        double b = this.r * strike * discountFactor(this.r, t) * cdf(BSD2());
        double c = this.r * F0 * discountFactor(r, t) * cdf(BSD1());
        double d = this.r * strike * discountFactor(this.r, t) * cdf(-BSD2());
        double f = this.r * F0 * discountFactor(this.r, t) * cdf(-BSD1());
        double result = option.isCallputFlag() ? a - b + c : a + d - f;
        logger.debug("交易号:"+this.instrument.getInstId()+"Black期权Theta = " + result);
        if (!option.isBuysellFlag()) {
            result *= -1;
        }
        return result * this.participateRate;
    }

    private Double calculateVega() {
        double result = F0 * discountFactor(this.r, t) * pdf(BSD1()) * Math.sqrt(t);
        logger.debug("交易号:"+this.instrument.getInstId()+"Black期权Vega = " + result);
        if (!option.isBuysellFlag()) {
            result *= -1;
        }
        return result * this.participateRate;
    }

    private Double calculateGammaAmount(Boolean isAdjusted) {
        double gamma = calculateGamma();

        if (!isAdjusted) {
            return position * gamma * Math.pow(F0, 2) / 100;
        } else {
            return 0.5 * gamma * Math.pow(F0 * vol / Math.sqrt(this.yearFrac), 2);
        }

    }

    private Double calculateGamma() {
        double p = pdf(BSD1());
        double result = discountFactor(this.r, t) * p / (F0 * vol * Math.sqrt(t));
        logger.debug("交易号:"+this.instrument.getInstId()+"Black期权Gamma = " + result);
        if (!option.isBuysellFlag()) {
            result *= -1;
        }
        return result * this.participateRate;
    }

    private Double calculateDeltaAmount(Boolean isAdjusted) {
        double delta = calculateDelta();

        if (!isAdjusted) {
            return delta * F0 * position;
        } else {
            return delta * F0 * position * this.vol / Math.sqrt(this.yearFrac);
        }
    }

    private Double calculateDelta() {
        double d1 = BSD1();
        double result = option.isCallputFlag() ? discountFactor(this.r, t) * cdf(d1) : -discountFactor(this.r, t) * (1 - cdf(d1));
        if (!option.isBuysellFlag()) {
            result *= -1;
        }
        logger.debug("交易号:"+this.instrument.getInstId()+"Black期权Delta = " + result);
        return result * this.participateRate;
    }


    /**
     * @author snow.li
     * @date 2022/2/16
     * @description 求解过程
     */
    // 求解BS模型的d1
    private double BSD1() {
        double a = 0, b = 0, c = 0, d1 = 0;
        a = Math.log(F0 / strike);
        b = (0.5 * Math.pow(vol, 2)) * t;
        c = vol * Math.sqrt(t);
        d1 = (a + b) / c;
        logger.debug("交易号:"+this.instrument.getInstId()+" D1:" + d1);
        return d1;
    }

    // 求解BS模型的d2
    private double BSD2() {
        double a = 0, b = 0, c = 0, d2 = 0;
        a = Math.log(F0 / strike);
        b = (-0.5 * Math.pow(vol, 2)) * t;
        c = vol * Math.sqrt(t);
        d2 = (a + b) / c;
        logger.debug("交易号:"+this.instrument.getInstId()+" D2:" + d2);
        return d2;
    }

    // BS模型计算外汇期权价格过程
    private double calculatePvByBlackodel(Double discountRate) {
        double pricing;
        // 赋值d1,d2
        double d1, d2;
        d1 = BSD1();
        d2 = BSD2();
        logger.debug("交易号:"+this.instrument.getInstId()+" D1:" + d1 + "  ---D2:" + d2);
        logger.debug("交易号:"+this.instrument.getInstId()+" N(D1):" + cdf(d1) + "  --- N(D2):" + cdf(d2));
        // 看涨看跌
        if (option.isCallputFlag()) {
            pricing = discountFactor(discountRate, t) * (F0 * cdf(d1) - strike * cdf(d2));
        } else {
            pricing = discountFactor(discountRate, t) * (strike * (1 - cdf(d2)) - F0 * (1 - cdf(d1)));
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
            return option.isCallputFlag() ? Math.max(0, F0 - strike) * position * participateRate
                    : Math.max(0, strike - F0) * participateRate * position;
        double pvPerUnit = calculatePvByBlackodel(discountRate);
//        this.pv = pvPerUnit;
        this.pv = pvPerUnit * participateRate * position;
        logger.debug("交易号:"+this.instrument.getInstId()+" 欧式期权价值: " + pv);
        return pv;
    }

    /**
     * @author qianchen
     * @date 2024/4/1
     * @description
     */
    private Double calculateOptionVolatility() {
        VolatilityType type = option.getVolatilityType();
        logger.debug("交易号:"+this.instrument.getInstId()+"香草期权(股票期货、商品期货){}波动率算法: {}", this.instrument.getInstId(), type);
        double result = 0.0;
        try {
            switch (type) {
                case HISTORICAL:
                    Price adjPrice = super.mds.getTypePrices(assetType, option.getSecurityCode());  // 计算波动率价格
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
                    surface = (ComSurface) super.mds.getVolSurface(surfaceId, SurfaceType.COM).getOneDateSurface(qlValDate);
                    strike = option.getStrike();
                    // 万得数据moneyness是 K/S
                    double moneyness = strike / F0;
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
        PriceType volPriceType;
        Price adjPrice = super.mds.getTypePrices(assetType, option.getSecurityCode());

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
