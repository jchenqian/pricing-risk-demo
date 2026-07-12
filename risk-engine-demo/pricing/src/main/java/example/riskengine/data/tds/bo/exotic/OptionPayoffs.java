package example.riskengine.data.tds.bo.exotic;

import example.riskengine.common.bases.time.QLDate;
import example.riskengine.common.enums.tds.OptionTermType;
import example.riskengine.common.enums.tds.PayoffType;
import example.riskengine.common.exceptions.EngineCodeEnums;
import example.riskengine.common.exceptions.EngineException;
import example.riskengine.data.tds.entity.ValFndRfdOptionPayoffs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author qianchen
 * @date 2023/6/6
 * @description 期权自定义模型payoff信息
*/
public class OptionPayoffs extends OptionReferences {


    /**
     * 存储当前交易编号的所有payoff信息
     */
    private List<OptionPayoffElement> optionPayoffList = new ArrayList<>();

    private Map<OptionTermType, List<OptionPayoffs.OptionPayoffElement>> optionPayoffsMap = new ConcurrentHashMap<>();

    public class OptionPayoffElement {
        /**
         * payoff类型
         */
        private PayoffType type;

        /**
         * payoff对应条款
         */
        private OptionTermType term;

        /**
         * 收益率参数1
         */
        private Double rate1 = 0.0;

        /**
         * 收益率参数2
         */
        private Double rate2 = 0.0;

        /**
         * 收益率参数3
         */
        private Double rate3 = 0.0;

        /**
         * term优先级
         */
        private int termOrder;

        /**
         * +/-
         */
        private Boolean isPlus;

        /**
         * 执行价格水平
         */
        private Double strikeCoef;

        /**
         * 到期日
         */
        private QLDate maturityDate;

        /**
         * 金额
         */
        private Double amount;

        /**
         * 累计数量
         */
        private Double accumulateUnits;

        private Double time = 1.0, participateRate = 1.0, accuCoef = 1.0;

        private Double St, strike, spot, protection, fixedPayment;
        private Double multiplier;
        private int businessDaysBefore, businessDaysAfter;


        public PayoffType getType() {
            return type;
        }

        public void setType(PayoffType type) {
            this.type = type;
        }

        public QLDate getMaturityDate() {
            return maturityDate;
        }

        public void setMaturityDate(QLDate maturityDate) {
            this.maturityDate = maturityDate;
        }

        public Boolean getPlus() {
            return isPlus;
        }

        public void setPlus(Boolean plus) {
            isPlus = plus;
        }

        public OptionTermType getTerm() {
            return term;
        }

        public void setTerm(OptionTermType term) {
            this.term = term;
        }

        public Double getRate1() {
            return rate1;
        }

        public void setRate1(Double rate1) {
            this.rate1 = rate1;
        }

        public Double getRate2() {
            return rate2;
        }

        public void setRate2(Double rate2) {
            this.rate2 = rate2;
        }

        public Double getRate3() {
            return rate3;
        }

        public void setRate3(Double rate3) {
            this.rate3 = rate3;
        }

        public int getTermOrder() {
            return termOrder;
        }

        public void setTermOrder(int termOrder) {
            this.termOrder = termOrder;
        }

        public Double getTime() {
            return time;
        }

        public void setTime(Double time) {
            this.time = time;
        }

        public Double getParticipateRate() {
            return participateRate;
        }

        public void setParticipateRate(Double participateRate) {
            this.participateRate = participateRate;
        }

        public Double getSt() {
            return St;
        }

        public void setSt(Double st) {
            St = st;
        }

        public Double getStrike() {
            return strike;
        }

        public void setStrike(Double strike) {
            this.strike = strike;
        }

        public Double getSpot() {
            return spot;
        }

        public void setSpot(Double spot) {
            this.spot = spot;
        }

        public Double getStrikeCoef() {
            return strikeCoef;
        }

        public void setStrikeCoef(Double strikeCoef) {
            this.strikeCoef = strikeCoef;
        }

        public Double getAmount() {
            return amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }

        public Double getAccumulateUnits() {
            return accumulateUnits;
        }

        public void setAccumulateUnits(Double accumulateUnits) {
            this.accumulateUnits = accumulateUnits;
        }

        public Double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(Double multiplier) {
            this.multiplier = multiplier;
        }

        public int getBusinessDaysBefore() {
            return businessDaysBefore;
        }

        public void setBusinessDaysBefore(int businessDaysBefore) {
            this.businessDaysBefore = businessDaysBefore;
        }

        public int getBusinessDaysAfter() {
            return businessDaysAfter;
        }

        public void setBusinessDaysAfter(int businessDaysAfter) {
            this.businessDaysAfter = businessDaysAfter;
        }

        public Double getAccuCoef() {
            return accuCoef;
        }

        public void setAccuCoef(Double accuCoef) {
            this.accuCoef = accuCoef;
        }

        public Double getFixedPayment() {
            return fixedPayment;
        }

        public void setFixedPayment(Double fixedPayment) {
            this.fixedPayment = fixedPayment;
        }

        @Override
        public String toString() {
            return "OptionPayoffElement{" +
                    "type=" + type +
                    ", term=" + term +
                    ", strikeCoef=" + strikeCoef +
                    ", time=" + time +
                    ", St=" + St +
                    ", strike=" + strike +
                    '}';
        }

        /**
         * @author qianchen
         * @date 2023/6/20
         * @description 根据payoff类型计算收益率
         * @para 计算全部使用成员变量, 在Engine中赋值, 未赋值则使用init
        */
        public double calculateRate() {
            double rate = 0.0;
            switch (this.type) {
                case FIXEDRATE:
                case FIXED_RATE:
                    rate = ( rate1 + rate2 + rate3 ) * time * participateRate;
                    break;
                case KNOCKOUT_FLOAT:
                case FLOAT:
                    rate = Math.min( (St - strike) / spot, 0.0) * participateRate;
                    break;
                case FLOAT_INVERSE:
                    rate = Math.min( (strike - St) / spot, 0.0) * participateRate;
                    break;
                case FLOOR:
                    rate = -Math.min(Math.max(0.0, St - strike) / spot, rate1) * participateRate;
                    break;
                case FLOOR_INVERSE:
                    rate = -Math.min(Math.max(0.0, strike - St) / spot, rate1) * participateRate;
                    break;
                case FIXED_DAILY:
                case POSITON_DAILY:
                    rate = amount * accuCoef;
                    break;
                case LONG_PUT_A:
                    rate = Math.max( (strike - St) / spot, 0.0) * participateRate;
                    break;
                case LONG_PUT_B:
                    rate = Math.max( (strike - St) / strike, 0.0) * participateRate;
                    break;
                case SPRING_KNOCKIN:
                    rate = Math.max( rate1 - 1, Math.min((St - strike) / spot, 0.0) * participateRate );
                    break;
                case DIVIDEND_YIELD:
                    rate = rate1 * participateRate;
                    break;
                case PHX_KNOCKIN:
                    rate = Math.max( -rate1, Math.min((St - strike) / spot, 0.0) * participateRate );
                    break;
                case PLAIN_RATE:
                    rate = rate1;
                    break;
                case LONG_CALL_A:
                    rate = Math.max((St - strike ) / spot, 0.0) * participateRate;
                    break;
                case LONG_CALL_B:
                    rate = Math.max((St - strike ) / strike, 0.0) * participateRate;
                    break;
                case ACCUMULATE_AMOUNT:
                case DAILY:
                    rate = amount * participateRate * accuCoef;
                    break;
                case LAST:
                    rate = amount * participateRate * accuCoef;
                    break;
                case LAST_EXTRA:
                    rate = amount * participateRate * accuCoef * businessDaysAfter;
                    break;
                case PROTECTION_ACCUMULATE_AMOUNT:
                    rate = amount * participateRate * rate1;
                    break;
                case ENHANCED_KNOCKOUT_FLOAT:
                    rate = ( (St - strike) / strike - rate1 ) * participateRate;
                    break;
                case PROTECTION:
                    rate = (St - protection) * amount * businessDaysAfter * participateRate;
                    break;
                case PROTECTION_FIXED_PAY:
                    rate = fixedPayment * amount * businessDaysAfter * participateRate;
                    break;
                case ASIAN_CALL_MIN:
                    rate = Math.max(St - strike, fixedPayment);
                    break;
                case ASIAN_PUT_MIN:
                    rate = Math.max(strike - St, fixedPayment);
                    break;
                case ASIAN_CALL_MAX:
                    rate = Math.max(Math.min(St, fixedPayment) - strike, 0);
                    break;
                case ASIAN_PUT_MAX:
                    rate = Math.max(strike - Math.min(St, fixedPayment), 0);
                    break;
                case ASIAN_CALL_MIN_MAX:
                    rate = Math.min(Math.max(St - strike, 0), fixedPayment - strike);
                    break;
                case ASIAN_PUT_MIN_MAX:
                    rate = Math.min(Math.max(strike - St, 0), strike - fixedPayment);
                    break;
                case UNDEFINEDPAYOFFTYPE:
                    throw new EngineException(EngineCodeEnums.ERROR_UNKNOWN_OPTION_PAYOFF, String.valueOf(this.type));
            }
            return rate;
        }
    }

    public OptionPayoffs() {
    }

    public void addPayoffs( ValFndRfdOptionPayoffs valFndRfdOptionPayoffs ) {
        OptionPayoffElement optionPayoffElement = new OptionPayoffElement();

        optionPayoffElement.type = PayoffType.valueOf(valFndRfdOptionPayoffs.getvPayoffType());
        if (optionPayoffElement.type.equals(PayoffType.UNDEFINEDPAYOFFTYPE))
            throw new EngineException(EngineCodeEnums.ERROR_UNKNOWN_OPTION_PAYOFF, valFndRfdOptionPayoffs.getvTradeNo());
        optionPayoffElement.term = OptionTermType.valueOf(valFndRfdOptionPayoffs.getvPayoffTerm());
        if (optionPayoffElement.term.equals(OptionTermType.UNDEFINEDTERMTYPE))
            throw new EngineException(EngineCodeEnums.ERROR_UNKNOWN_OPTION_TERM, valFndRfdOptionPayoffs.getvTradeNo());
        optionPayoffElement.termOrder = valFndRfdOptionPayoffs.getnTermsOrder();
        optionPayoffElement.isPlus = "1".equals(valFndRfdOptionPayoffs.getvIsPlus());
        optionPayoffElement.strikeCoef = valFndRfdOptionPayoffs.getnStrike();
        optionPayoffElement.protection = valFndRfdOptionPayoffs.getnProtectPrice();
        optionPayoffElement.fixedPayment = valFndRfdOptionPayoffs.getnFixedPayoff();

        // 若为空则使用初始化赋值
        if (null != valFndRfdOptionPayoffs.getnRate1())
            optionPayoffElement.rate1 = valFndRfdOptionPayoffs.getnRate1();
        if (null != valFndRfdOptionPayoffs.getnRate2())
            optionPayoffElement.rate2 = valFndRfdOptionPayoffs.getnRate2();
        if (null != valFndRfdOptionPayoffs.getnRate3())
            optionPayoffElement.rate3 = valFndRfdOptionPayoffs.getnRate3();
        if (null != valFndRfdOptionPayoffs.getnParticipateRate())
            optionPayoffElement.participateRate = valFndRfdOptionPayoffs.getnParticipateRate();
        if ( null != valFndRfdOptionPayoffs.getnAccFactor())
            optionPayoffElement.accuCoef = valFndRfdOptionPayoffs.getnAccFactor();

        this.optionPayoffList.add(optionPayoffElement);
    }

    public void addOptionPayoffByTerm(OptionTermType term, OptionPayoffs.OptionPayoffElement onePayoff) {
        if (!this.optionPayoffsMap.containsKey(term)) {
            List<OptionPayoffElement> temp = new ArrayList<>();
            temp.add(onePayoff);
            this.optionPayoffsMap.put(term, temp);
        } else
            this.optionPayoffsMap.get(term).add(onePayoff);
    }

    public List<OptionPayoffElement> getOptionPayoffList() {
        return optionPayoffList;
    }

    public void setOptionPayoffList(List<OptionPayoffElement> optionPayoffList) {
        this.optionPayoffList = optionPayoffList;
    }

    public Map<OptionTermType, List<OptionPayoffElement>> getOptionPayoffsMap() {
        return optionPayoffsMap;
    }

    public void setOptionPayoffsMap(Map<OptionTermType, List<OptionPayoffElement>> optionPayoffsMap) {
        this.optionPayoffsMap = optionPayoffsMap;
    }
}
