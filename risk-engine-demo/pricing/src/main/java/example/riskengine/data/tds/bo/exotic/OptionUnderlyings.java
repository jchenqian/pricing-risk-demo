package example.riskengine.data.tds.bo.exotic;

import example.riskengine.common.bases.termstructures.yieldcurves.ZeroCurve;
import example.riskengine.common.bases.time.Schedule;
import example.riskengine.common.enums.mds.PriceType;
import example.riskengine.common.enums.tds.VolatilityType;
import example.riskengine.data.mds.bo.price.Price;
import example.riskengine.data.tds.entity.ValFndRfdOptionUnderlyings;

import java.util.ArrayList;
import java.util.List;

/**
 * @author qianchen
 * @date 2023/6/6
 * @description 期权自定义模型标的信息
*/
public class OptionUnderlyings extends OptionReferences {

    /**
     * 存储当前交易编号的所有标的信息
     */
    private List<OptionUnderlyingElement> optionUnderlyingElementList = new ArrayList<>();

    public void OptionUnderlyings() {
    }

    /**
     * @author qianchen
     * @date 2023/6/7
     * @description 期权标的信息实体化对象
    */
    public void addUnderlyings(ValFndRfdOptionUnderlyings valFndRfdOptionUnderlyings) {
        OptionUnderlyingElement optionUnderlyingElement = new OptionUnderlyingElement();

        optionUnderlyingElement.underlyingCode = valFndRfdOptionUnderlyings.getvUnderlyingCode();
        optionUnderlyingElement.type = PriceType.valueOf(valFndRfdOptionUnderlyings.getvAssetType());

        optionUnderlyingElement.spotPrice = valFndRfdOptionUnderlyings.getnSpotPrice();
//        optionUnderlyingElement.strike = valFndRfdOptionUnderlyings.getnStrikePriceCoef() * valFndRfdOptionUnderlyings.getnSpotPrice();
        optionUnderlyingElement.participateRate = valFndRfdOptionUnderlyings.getnUnderlyingWeight();

        optionUnderlyingElement.volPriceType = example.riskengine.common.enums.mds.PriceType.valueOf(valFndRfdOptionUnderlyings.getvVolPriceType());
        optionUnderlyingElement.dividendType = PriceType.DIVIDEND;
        optionUnderlyingElement.obsPriceType = PriceType.STOCK;

        optionUnderlyingElement.volType = VolatilityType.valueOf(valFndRfdOptionUnderlyings.getvVolType());
        optionUnderlyingElement.volBaseNumber = valFndRfdOptionUnderlyings.getnVolBase();
        optionUnderlyingElement.volatility = valFndRfdOptionUnderlyings.getnVol();
        optionUnderlyingElement.volBaseNumber = valFndRfdOptionUnderlyings.getnVolBase();
        optionUnderlyingElement.dividend = valFndRfdOptionUnderlyings.getnDividendyield();
//        optionUnderlyingElement.fxSpot = valFndRfdOptionUnderlyings.getnFx();
        optionUnderlyingElement.rho = valFndRfdOptionUnderlyings.getnRho();
        optionUnderlyingElement.rfr = valFndRfdOptionUnderlyings.getnRf();
        optionUnderlyingElement.yearFrac = valFndRfdOptionUnderlyings.getnYearfrac();

        optionUnderlyingElement.baseCurveName = valFndRfdOptionUnderlyings.getvCurve1();
        optionUnderlyingElement.simuCurveName = valFndRfdOptionUnderlyings.getvCurve2();
        optionUnderlyingElement.currencyPair = valFndRfdOptionUnderlyings.getvCcpair();
        optionUnderlyingElement.surfaceName = valFndRfdOptionUnderlyings.getvVolatilityId();
        optionUnderlyingElement.calendarName = valFndRfdOptionUnderlyings.getvCalendar();

        this.optionUnderlyingElementList.add(optionUnderlyingElement);
    }

    public class OptionUnderlyingElement{
        /**
         * 标的类型
         */
        private example.riskengine.common.enums.mds.PriceType type;

        /**
         * 标的代码
         */
        private String underlyingCode;

        /**
         * 标的参与率
         */
        private Double participateRate;

        /**
         * 期初价格
         */
        private Double spotPrice;

        /**
         * 行权价格
         */
        private Double strike;

        /**
         * 波动率使用价格类型
         */
        private example.riskengine.common.enums.mds.PriceType volPriceType;

        /**
         * 股息率使用类型
         */
        private example.riskengine.common.enums.mds.PriceType dividendType;

        /**
         * 波动率
         */
        private Double volatility;

        private Double originalVolatility; // 初始波动率，用于敏感度计算中还原初始数据

        /**
         * 波动率计算天数
         */
        private Double volBaseNumber;

        /**
         * 股息率
         */
        private Double dividend;

        /**
         * 无风险利率
         */
        private Double rfr, originalRfr;

        /**
         * 标的与汇率相关系数
         */
        private Double rho;

        /**
         * 折现曲线
         */
        private String baseCurveName;

        /**
         * 模拟曲线
         */
        private String simuCurveName;

        /**
         * 波动率曲面货币对
         */
        private String currencyPair;

        /**
         * 标的日行情价格
         */
        private Price underlyingPrice;

        private Price orignalunderlyingPrice = new Price(); // 用于计算Delet和Vega时，还原市场价格数据

        /**
         * 模拟曲线
         */
        private ZeroCurve simuCurve;


        /**
         * 股票计算波动率补足使用的行业代码
         */
        private String indexCode;

        /**
         * 金融日历
         */
        private String calendarName;

        /**
         * 非同种货币数量调整
         */
        private double quantoAdj;

        /**
         * 波动率曲面
         */
        private String surfaceName;

        /**
         * 预期收益率
         */
        private double drift;

        /**
         * 年化天数
         */
        private double yearFrac;

        private PriceType obsPriceType;

        private Schedule simuSchedule;

        /**
         * 波动率算法
         */
        private VolatilityType volType;

// ---------------------------------------------------------------
        public String getIndexCode() {
            return indexCode;
        }
        public void setIndexCode(String indexCode) {
            this.indexCode = indexCode;
        }
        public double getYearFrac() {
            return yearFrac;
        }
        public void setYearFrac(double yearFrac) {
            this.yearFrac = yearFrac;
        }

        public double getDrift() {
            return drift;
        }

        public void setDrift(double drift) {
            this.drift = drift;
        }
        public Schedule getSimuSchedule() {
            return simuSchedule;
        }
        public void setSimuSchedule(Schedule simuSchedule) {
            this.simuSchedule = simuSchedule;
        }

        public String getSurfaceName() {
            return surfaceName;
        }

        public void setSurfaceName(String surfaceName) {
            this.surfaceName = surfaceName;
        }

        public example.riskengine.common.enums.mds.PriceType getType() {
            return type;
        }

        public void setType(example.riskengine.common.enums.mds.PriceType type) {
            this.type = type;
        }

        public String getUnderlyingCode() {
            return underlyingCode;
        }

        public void setUnderlyingCode(String underlyingCode) {
            this.underlyingCode = underlyingCode;
        }

        public Double getParticipateRate() {
            return participateRate;
        }

        public void setParticipateRate(Double participateRate) {
            this.participateRate = participateRate;
        }

        public Double getSpotPrice() {
            return spotPrice;
        }

        public void setSpotPrice(Double spotPrice) {
            this.spotPrice = spotPrice;
        }

        public Double getStrike() {
            return strike;
        }

        public void setStrike(Double strike) {
            this.strike = strike;
        }

        public example.riskengine.common.enums.mds.PriceType getVolPriceType() {
            if (this.volPriceType != null) {
                return volPriceType;
            }
            return PriceType.STOCK;
        }

        public void setVolPriceType(example.riskengine.common.enums.mds.PriceType volPriceType) {
            this.volPriceType = volPriceType;
        }

        public example.riskengine.common.enums.mds.PriceType getDividendType() {
            if (this.dividend != null) {
                return dividendType;
            }
            return PriceType.DIVIDEND;
        }

        public void setDividendType(example.riskengine.common.enums.mds.PriceType dividendType) {
            this.dividendType = dividendType;
        }

        public Double getVolatility() {
            return volatility;
        }

        public void setVolatility(Double volatility) {
            this.volatility = volatility;
        }

        public Double getVolBaseNumber() {
            return volBaseNumber;
        }

        public void setVolBaseNumber(Double volBaseNumber) {
            this.volBaseNumber = volBaseNumber;
        }

        public Double getDividend() {
            return dividend;
        }

        public void setDividend(Double dividend) {
            this.dividend = dividend;
        }

        public Double getRfr() {
            return rfr;
        }

        public void setRfr(Double rfr) {
            this.rfr = rfr;
        }

        public Double getRho() {
            return rho;
        }

        public void setRho(Double rho) {
            this.rho = rho;
        }

        public String getBaseCurveName() {
            return baseCurveName;
        }

        public void setBaseCurveName(String baseCurveName) {
            this.baseCurveName = baseCurveName;
        }

        public String getSimuCurveName() {
            return simuCurveName;
        }

        public void setSimuCurveName(String simuCurveName) {
            this.simuCurveName = simuCurveName;
        }

        public String getCurrencyPair() {
            return currencyPair;
        }

        public void setCurrencyPair(String currencyPair) {
            this.currencyPair = currencyPair;
        }

        public ZeroCurve getSimuCurve() {
            return simuCurve;
        }

        public void setSimuCurve(ZeroCurve simuCurve) {
            this.simuCurve = simuCurve;
        }

        public String getCalendarName() {
            return calendarName;
        }
        public void setCalendarName(String calendarName) {
            this.calendarName = calendarName;
        }

        public Price getUnderlyingPrice() {
            return underlyingPrice;
        }

        public void setUnderlyingPrice(Price underlyingPrice) {
            this.underlyingPrice = underlyingPrice;
        }

        public double getQuantoAdj() {
            return quantoAdj;
        }

        public void setQuantoAdj(double quantoAdj) {
            this.quantoAdj = quantoAdj;
        }

        public PriceType getObsPriceType() {
            return obsPriceType;
        }

        public void setObsPriceType(PriceType obsPriceType) {
            this.obsPriceType = obsPriceType;
        }

        public VolatilityType getVolType() {
            return volType;
        }

        public void setVolType(VolatilityType volType) {
            this.volType = volType;
        }

        public Price getOrignalunderlyingPrice() {
            return orignalunderlyingPrice;
        }

        public void setOriginalVolatility(Double originalVolatility) {
            this.originalVolatility = originalVolatility;
        }

        public Double getOriginalVolatility() {
            return originalVolatility;
        }

        public Double getOriginalRfr() {
            return originalRfr;
        }

        public void setOriginalRfr(Double originalRfr) {
            this.originalRfr = originalRfr;
        }
    }

    public List<OptionUnderlyingElement> getOptionUnderlyingElementList() {
        return optionUnderlyingElementList;
    }

    public void setOptionUnderlyingElementList(List<OptionUnderlyingElement> optionUnderlyingElementList) {
        this.optionUnderlyingElementList = optionUnderlyingElementList;
    }
}
