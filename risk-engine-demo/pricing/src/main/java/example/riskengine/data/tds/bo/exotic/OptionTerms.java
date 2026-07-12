package example.riskengine.data.tds.bo.exotic;

import example.riskengine.common.bases.math.matrixutilities.Matrix;
import example.riskengine.common.bases.time.Frequency;
import example.riskengine.common.bases.time.QLDate;
import example.riskengine.common.enums.tds.ExerciseType;
import example.riskengine.common.enums.tds.OptionTermType;
import example.riskengine.common.exceptions.EngineCodeEnums;
import example.riskengine.common.exceptions.EngineException;
import example.riskengine.data.tds.entity.ValFndRfdOptionTerms;

import java.util.*;

/**
 * @author qianchen
 * @date 2023/6/6
 * @description 期权自定义模型条款信息
*/
public class OptionTerms extends OptionReferences{

    /**
     * 存储当前交易编号的所有条款信息
     */
    private List<OptionTermElement> optionTermList = new ArrayList<>();
    
    /**
     * 不参与触发判断的条款集合
     */
    private final Set<OptionTermType> Excluded = new HashSet<>(Arrays.asList(OptionTermType.DIVIDEND, OptionTermType.PROTECTION, OptionTermType.ACCUMULATE));


    public void addTerms(ValFndRfdOptionTerms valFndRfdOptionTerms) {

        OptionTermElement optionOneTerm = new OptionTermElement();

        optionOneTerm.term = OptionTermType.valueOf(valFndRfdOptionTerms.getvTerm());
        if (optionOneTerm.equals(OptionTermType.UNDEFINEDTERMTYPE))
            throw new EngineException(EngineCodeEnums.ERROR_UNKNOWN_OPTION_TERM, valFndRfdOptionTerms.getvTradeNo());
        optionOneTerm.level = valFndRfdOptionTerms.getnLevel();
        optionOneTerm.participateRate = valFndRfdOptionTerms.getnParticipateRate();
        optionOneTerm.order = valFndRfdOptionTerms.getnTermsOrder();
        optionOneTerm.obsFrequency = Frequency.valueOf(valFndRfdOptionTerms.getvObsType());
        optionOneTerm.startDate = new QLDate(valFndRfdOptionTerms.getdTermStartDate());
        optionOneTerm.endDate = new QLDate(valFndRfdOptionTerms.getdTermEndDate());
        optionOneTerm.obsDirection = ExerciseType.valueOf(valFndRfdOptionTerms.getvDirection());

        optionTermList.add(optionOneTerm);
    }

    public class OptionTermElement {

        /**
         * 条款类型
         */
        private OptionTermType term;

        /**
         * 条款执行水平
         */
        private Double level;

        /**
         * 条款参与率
         */
        private Double participateRate;

        /**
         * 条款优先级顺序
         */
        private int order;

        /**
         * 条款开始日、结束日
         */
        private QLDate startDate, endDate;

        /**
         * 条款观察方式
         */
        private Frequency obsFrequency;

        /**
         * 条款是否触发
         */
        private Boolean isTrue;

        /**
         * 条款触发矩阵
         */
        private Matrix statusMatrix;

        /**
         * 条款当天是否触发
         */
        private Boolean isTrueByDay;

        /**
         * 条款触发判断方向
         */
        private ExerciseType obsDirection;



        // -------------------------------


        public Boolean getTrue() {
            return isTrue;
        }

        public void setTrue(Boolean aTrue) {
            isTrue = aTrue;
        }

        public Boolean getTrueByDay() {
            return isTrueByDay;
        }

        public void setTrueByDay(Boolean trueByDay) {
            isTrueByDay = trueByDay;
        }

        public OptionTermType getTerm() {
            return term;
        }

        public void setTerm(OptionTermType term) {
            this.term = term;
        }

        public Double getLevel() {
            return level;
        }

        public void setLevel(Double level) {
            this.level = level;
        }

        public Double getParticipateRate() {
            return participateRate;
        }

        public void setParticipateRate(Double participateRate) {
            this.participateRate = participateRate;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
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

        public Frequency getObsFrequency() {
            return obsFrequency;
        }

        public void setObsFrequency(Frequency obsFrequency) {
            this.obsFrequency = obsFrequency;
        }

        public Matrix getStatusMatrix() {
            return statusMatrix;
        }

        public void setStatusMatrix(Matrix statusMatrix) {
            this.statusMatrix = statusMatrix;
        }

        public ExerciseType getObsDirection() {
            return obsDirection;
        }

        public void setObsDirection(ExerciseType obsDirection) {
            this.obsDirection = obsDirection;
        }

        @Override
        public String toString() {
            return "OptionTermElement{" +
                    "条款类型" + term +
                    "条款水平" + level +
                    ", 条款优先级顺序=" + order +
                    ", 条款判断方向=" + obsDirection +
                    '}';
        }
    }

    public OptionTerms() {}


    /**
     * @author qianchen
     * @date 2023/6/20
     * @description 排sorted termList中找到第一个触发的条款, 若都未触发return default
    */
    public OptionTermType getFirstByPath(List<OptionTermElement> optionTermList) {
        for (OptionTermElement temp : optionTermList) {
            if (temp.isTrue && (! Excluded.contains(temp.getTerm()))) 
                return temp.getTerm();
        }
        return OptionTermType.DEFAULT;
    }

    /**
     * @author qianchen
     * @date 2023/10/8
     * @description 排sorted termList中找到第一个触发的条款, 若都未触发return default
     */
    public OptionTermType getFirstByDay(List<OptionTermElement> optionTermList) {
        for (OptionTermElement temp : optionTermList) {
            if (temp.isTrueByDay && (! Excluded.contains(temp.getTerm())))
                return temp.getTerm();
        }
        return OptionTermType.DEFAULT;
    }

    public Boolean checkFlagByDay(QLDate currentDate, OptionTermElement current, Double price, Double spot, Boolean isCallOption) {
        Boolean result = false;

        if (currentDate.gt(current.endDate) || currentDate.lt(current.startDate))
            return result;

        OptionTermType type = current.term;
        double barrier = Math.round(current.level * spot);
        switch(type) {
            case KNOCK_OUT:
                result = isCallOption ? (price >= barrier) : (price <= barrier);
                break;
            case KNOCK_IN:
                result = isCallOption ? (price <= barrier) : (barrier <= price);
                break;
            default:
                break;
        }
        return result;
    }
    

//    -------------------------------------------------------------

    public List<OptionTermElement> getOptionTermList() {
        return optionTermList;
    }

    public void setOptionTermList(List<OptionTermElement> optionTermList) {
        this.optionTermList = optionTermList;
    }
}
