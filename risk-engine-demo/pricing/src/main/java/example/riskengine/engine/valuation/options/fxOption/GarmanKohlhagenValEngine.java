package example.riskengine.engine.valuation.options.fxOption;

import example.riskengine.common.bases.termstructures.surfaces.FxSurface;
import example.riskengine.common.bases.termstructures.yieldcurves.ZeroCurve;
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
import example.riskengine.data.tds.bo.exotic.OptionUnderlyings;
import example.riskengine.data.tds.bo.options.FxOption.EuFxOption;
import example.riskengine.engine.valuation.BaseOptionValEngine;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;

/**
 * @author qianchen
 * @date 2024/4/1
 * @description 外汇期权GK模型, 含敏感度方案, 统一使用BLACK_SCHOLES_USERMODEL
 */

@Component("FXOPTION_OPTION_EU_VALMODEL_BLACK_SCHOLES_USERMODEL")
@Scope("prototype")
public class GarmanKohlhagenValEngine extends BaseOptionValEngine {
    private final DecimalFormat format = new DecimalFormat("######0.00000000");
    private final static double bp = 0.0001;
    private EuFxOption option;
    private Double volAtm;
    private ZeroCurve baseCurve;//本币折现曲线
    private ZeroCurve refCurve;//外币折现曲线
    private FxSurface surface;//曲面名称
    private Double r1;//根据剩余期限插值得到的本币折现曲线利率
    private Double r2;//根据剩余期限插值得到的外币折现曲线利率
    private Double t;//余期限
    private Double strike;//即期汇率执行价
    private Double spot;//即期汇率
    private Double nominal;//名义本金
    private boolean callPutFlag;//看涨看跌标识
    private boolean buySellFlag;//买入卖出标识
    private String resultCurrency;
    private double position, yearFrac, pv;
    private Calendar calendar;
    private QLDate maturity;
    private Double participateRate;

    @Override
    protected void prepare() {
        option = (EuFxOption) this.instrument;
        this.position = option.getPriorPosition();
        this.yearFrac = option.getYearFrac();
        this.maturity = option.getMaturityDate();
        this.calendar = super.rds.getCalendars().getCalendar(option.getCalendarName());
        String currencyPair = option.getCurrencyPair();
        PriceType fxType = PriceType.valueOf(option.getFxType());
        this.spot = super.mds.getTypePriceByDay(fxType, currencyPair, qlValDate);

        String baseCurveName = option.getBaseCurve();
        String refCurveName = option.getReferenceCurve();
        // 获取曲线/曲面数据
        this.t = option.getDayCounter().yearFraction(qlValDate, maturity);
        this.baseCurve = super.mds.getIrCurve(baseCurveName).getOneDateCurve(qlValDate);
        this.refCurve = super.mds.getIrCurve(refCurveName).getOneDateCurve(qlValDate);

        // 增加逻辑判断是否配置固定折现率
        Double baseRfr = option.getBaseRfr();
        Double refRfr = option.getRefRfr();
        //
        this.r1 = (baseRfr != null)?baseRfr:baseCurve.getZeroYield(t);
        this.r2 = (refRfr != null)?refRfr:refCurve.getZeroYield(t);
        this.volAtm = calculateOptionVolatility();

        // 获取期权计算必备数据项
        this.strike = option.getStrike();
        this.nominal = option.getNotional();
        this.callPutFlag = option.isCallputFlag();  // 看涨看跌标识
        this.buySellFlag = option.isBuysellFlag();  // 买入卖出标识
        this.resultCurrency = option.getReferenceCurrency();  // 结算币种
        this.participateRate = option.getParticipateRate();
    }

    @Override
    protected Double calc(IndicatorType indicatorType) {
        Double result = null;
        switch (indicatorType) {
            case PV:
                result = calculatePv(this.r1) * position * participateRate;
                break;
            case DIVIDEND:
                result = this.r2;
                break;
            case VOLATILITY:
                result = volAtm;
                break;
            case RFR:
                result = r1;
                break;
            case D1:
                result = GkD1();
                break;
            case D2:
                result = GkD2();
                break;
            case SPOTPRICE:
                result = spot;
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
                result = calculateVega() * this.volAtm * position;
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


    private Double calculateTheta() {
        double a = -0.5 * discountFactor(r1, t) * spot * pdf(GkD1()) * volAtm / Math.sqrt(t);
        if (option.isCallputFlag()) {
            double b = r2 * strike * discountFactor(r2, t) * cdf(GkD2());
            double c = r1 * spot * discountFactor(r1, t) * cdf(GkD1());
            double result = a - b + c;
            if (buySellFlag == false) {
                result *= -1;
            }
            return result;
        } else {
            double b = r2 * strike * discountFactor(r2, t) * cdf(-GkD2());
            double c = r1 * spot * discountFactor(r1, t) * cdf(-GkD1());
            double result = a + b - c;
            if (buySellFlag == false) {
                result *= -1;
            }
            return result;
        }
    }

    /**
     * @author ziyuan.xu
     * @date 2021/8/9
     * @description 估值
     */
    double calculatePv(Double discountRate) {
        double pips = calculatePvByGarmanKohlhagen(discountRate);
//        this.pv = pips * nominal;
        this.pv = pips;
        logger.debug("交易号:"+this.instrument.getInstId()+"欧式外汇期权单位价格 : " + format.format(pips));
//        logger.debug("欧式外汇期权名义本金 : " + nominal);
//        logger.debug("欧式外汇期权估值结果 : " + format.format(pv));

        return pv;
    }

    /**
     * @author ziyuan.xu
     * @date 2021/8/9
     * @description G-K模型计算外汇期权价格
     */
    private double calculatePvByGarmanKohlhagen(Double discountRate) {
        double d1, d2;
        d1 = GkD1(discountRate);
        d2 = GkD2(discountRate);

        double pips;
        //看涨看跌
        if (this.callPutFlag == true) {
            logger.debug("交易号:"+this.instrument.getInstId()+" spot: " + spot + " 折现率1: " + discountFactor(discountRate, t) + " N(d1): " + cdf(d1));
            logger.debug("交易号:"+this.instrument.getInstId()+" strike: " + strike + " 折现率2: " + discountFactor(r2, t) + " N(d2): " + cdf(d2));
            pips = spot * discountFactor(discountRate, t) * cdf(d1) - strike * discountFactor(r2, t) * cdf(d2);
        } else {
            pips = strike * discountFactor(r2, t) * (1 - cdf(d2)) - spot * discountFactor(discountRate, t) * (1 - cdf(d1));
        }

        //买入卖出
        if (buySellFlag == false) {
            pips = -pips;
        }
        return pips;
    }

    /**
     * @author ziyuan.xu
     * @date 2021/4/2
     * @description 根据G-K计算的d1
     */
    private double GkD1(Double discountRate) {
        double a = 0, b = 0, c = 0, d1 = 0;
        a = Math.log(spot / strike);
        b = (discountRate - r2 + 0.5 * Math.pow(volAtm, 2)) * t;
        c = volAtm * Math.sqrt(t);
        d1 = (a + b) / c;
        return d1;
    }

    private double GkD1() {
        return GkD1(this.r1);
    }

    /**
     * @author ziyuan.xu
     * @date 2021/5/11
     * @description 根据G-K计算的d2
     */
    private double GkD2(Double discountRate) {
        double a = 0, b = 0, c = 0, d2 = 0;
        a = Math.log(spot / strike);
        b = (discountRate - r2 - 0.5 * Math.pow(volAtm, 2)) * t;
        c = volAtm * Math.sqrt(t);
        d2 = (a + b) / c;
        return d2;
    }

    private double GkD2() {
        return GkD2(this.r1);
    }


    private double calculateDelta() {
        double d1 = GkD1();
        double df = discountFactor(r1, t);
        double result = option.isCallputFlag() ? cdf(d1) : -cdf(-d1);
        logger.debug("交易号:"+this.instrument.getInstId()+"GK期权Delta = " + result);
        if (!buySellFlag) {
            result *= -1;
        }
        return result * df;
    }

    private Double calculateDeltaAmount(Boolean isAdjusted) {
        double delta = calculateDelta();

        if (!isAdjusted) {
            return delta * spot * position;
        } else {
            return delta * spot * position * this.volAtm / Math.sqrt(yearFrac);
        }
    }

    private double calculateVega() {
        double result = spot * discountFactor(r1, t) * pdf(GkD1()) * Math.sqrt(t);
        if (!buySellFlag) {
            result *= -1;
        }
        return result;
    }


    private double calculateGamma() {
        double result = discountFactor(r1, t) * pdf(GkD1()) / (spot * volAtm * Math.sqrt(t));
        if (!buySellFlag) {
            result *= -1;
        }
        return result;
    }

    private Double calculateGammaAmount(Boolean isAdjusted) {
        double gamma = calculateGamma();

        if (!isAdjusted) {
            return position * gamma * Math.pow(spot, 2) / 100;
        } else {
            return 0.5 * gamma * Math.pow(spot * volAtm / Math.sqrt(yearFrac), 2);
        }

    }


    /**
     * @author Zi
     * @date 29/05/2023
     * @description
     */
    private double calculateRho() {
        double result;
        if (option.isCallputFlag()) {
            result = strike * t * discountFactor(r2, t) * cdf(GkD2());
        } else {
            result = -strike * t * discountFactor(r2, t) * cdf(-GkD2());
        }
        if (!buySellFlag) {
            result *= -1;
        }
        return result;
    }

    private Double calculateDV01() {
        double r = this.r2;
        this.r2 += this.bp;
        double up = calculatePv(this.r1);
        this.r2 -= 2 * this.bp;
        double down = calculatePv(this.r1);
        this.r2 = r;
        return (up - down) * position/ 2;
    }

    /**
     * @author qianchen
     * @date 2024/4/1
     * @description
     */
    private Double calculateOptionVolatility() {
        VolatilityType type = option.getVolatilityType();
        logger.debug("交易号:"+this.instrument.getInstId()+"外汇香草期权{}波动率算法: {}", this.instrument.getInstId(), type);
        double result = 0.0;
        try {
            switch (type) {
                case HISTORICAL:
                    // 外汇期权不支持历史波动率算法
                    logger.error("交易号:"+this.instrument.getInstId()+"tradeNo = " + this.instrument.getInstId() + " error!!!");
                    throw new EngineException(EngineCodeEnums.ERROR_UNMATCHED_VOLATILITY_TYPE);
                case IMPLIED:
                    String surfaceId = option.getVolSurface();
                    surface = (FxSurface) super.mds.getVolSurface(surfaceId, SurfaceType.FX).getOneDateSurface(qlValDate);
                    double volTerm = this.surface.getDayCounter().yearFraction(this.qlValDate, this.instrument.getMaturityDate());
                    result = surface.getVol(volTerm, 50.0); // smile-off算法
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
        double vol = 0.0;
        PriceType fxType = PriceType.valueOf(option.getFxType());
        Price adjPrice = super.mds.getTypePrices(fxType, option.getCurrencyPair());  // 计算波动率价格

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
