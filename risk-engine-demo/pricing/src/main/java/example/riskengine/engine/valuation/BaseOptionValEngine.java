package example.riskengine.engine.valuation;

import example.riskengine.common.QL;
import example.riskengine.common.bases.daycounters.DayCounter;
import example.riskengine.common.bases.math.matrixutilities.Array;
import example.riskengine.common.bases.math.matrixutilities.CholeskyDecomposition;
import example.riskengine.common.bases.math.matrixutilities.Matrix;
import example.riskengine.common.bases.time.*;
import example.riskengine.common.bases.tools.HistoricalVol;
import example.riskengine.common.enums.mds.PriceType;
import example.riskengine.common.enums.rds.RandomType;
import example.riskengine.common.enums.tds.OptionTermType;
import example.riskengine.common.enums.tds.VolatilityType;
import example.riskengine.common.exceptions.EngineCodeEnums;
import example.riskengine.common.exceptions.EngineException;
import example.riskengine.data.mds.bo.price.Price;
import example.riskengine.data.tds.bo.exotic.OneForAll;
import example.riskengine.data.tds.bo.exotic.OptionTerms;
import example.riskengine.data.tds.bo.exotic.OptionUnderlyings;
import example.riskengine.data.tds.bo.options.Option;
import example.riskengine.data.tds.bo.options.StockOption.EuStockOption;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author ziyuan.xu
 * @date 2021/4/1
 * @description
 */
public abstract class BaseOptionValEngine extends AbstractInstValEngine {

    protected static final Logger logger = LoggerFactory.getLogger(BaseOptionValEngine.class);
    private DecimalFormat format = new DecimalFormat("######0.000000");
    protected Matrix randoms;
    protected List<Matrix> randnMatrixs =new ArrayList<>();
    protected Matrix multiUnderlyingsRandomMatrix;
    protected DayCounter dayCounter;

    /**
     * @author ziyuan.xu
     * @date 2021/5/11
     * @description 根据给定的r，t计算连续复利的discount factor
     */
    public double discountFactor(double r, double t) {
        double df = Math.exp(-r * t);
        return df;
    }

    /**
     * @author ziyuan.xu
     * @date 2021/5/11
     * @description 计算正态分布的概率分布函数pdf
     */
    public double pdf(double x) {
        NormalDistribution normalDistribution = new NormalDistribution(0, 1);
        double result = normalDistribution.density(x);
        return result;
    }

    /**
     * @author ziyuan.xu
     * @date 2021/5/11
     * @description 计算正态分布的累计分布函数cdf
     */
    public double cdf(double x) {
        NormalDistribution normalDistribution = new NormalDistribution(0, 1);
        double result = normalDistribution.cumulativeProbability(x);
        return result;
    }

    /**
     * @author ziyuan.xu
     * @date 2022/2/16
     * @param r, q
     * @description Monte-Carlo Simulation
     */
    public Double MonteCarloSimulation(Double S0, Double T, Double r, Double q, Double vol) {
        Double St, epsilon, drift;
        Random random = new Random();
        epsilon = random.nextGaussian();
//        Double u=0.0, v=0.0, a, b;
//        u = Math.random();
//        v = Math.random();
//        a = Math.sqrt(-2*Math.log(u));
//        b = Math.cos(2*Math.PI*v);
//        epsilon = a * b;
        drift = r - q - 0.5 * Math.pow(vol, 2);
        St = S0 * Math.exp(drift * T + vol * Math.sqrt(T) * epsilon);
        return St;
    }

    /**
     * @author Zi
     * @date 15/08/2022
     * @param r, q, quantoAdjust
     * @description Monte-Carlo Simulation with Quanto-Adjust
     */
    public Double MonteCarloSimulation(Double S0, Double T, Double r, Double q,
                                       Double quanto, Double vol) {
        Double St, epsilon, drift;
        Random random = new Random();
        epsilon = random.nextGaussian();
        drift = r - q - quanto - 0.5 * Math.pow(vol, 2);
        St = S0 * Math.exp(drift * T + vol * Math.sqrt(T) * epsilon);
        return St;
    }

    /**
     * @author Zi
     * @date 11/04/2023
     * @description
     */
    public List<Matrix> GenerateMonteCarloMatrix(Array S0, Array r, Array q, Array quanto,
                                                 Array vol, int path, ArrayList<Double> tList,
                                                 Matrix corrcoef){
        final int number = S0.size();
        //相关系数矩阵分解结果：上三角矩阵L
        CholeskyDecomposition cholesky = new CholeskyDecomposition(corrcoef);
        Matrix L = cholesky.L().transpose();
        //初始化矩阵list
        List<Matrix> matrixList = new ArrayList<>();
        for(int n=0; n<number;n++){
            Matrix m = new Matrix(tList.size()+1,path);
            matrixList.add(m);
        }
        for(int i=0; i<path; i++){
            Matrix random =  zeros(tList.size() + 1,number);
            random.fill(new Random());
            Matrix M = random.mul(L);
            M.fillCol(0, S0);
            Array drift = r.sub(q.add(quanto.add(vol.mul(vol))));

            double tau;
            for (int j = 0; j < tList.size() - 1; j++) {
                if (j == 0) {
                    tau = tList.get(j);
                } else {
                    tau = tList.get(j+1) - tList.get(j);
                }
                Array c = M.rangeCol(j+1).mul(vol.mul(Math.sqrt(tau))).add(drift.mul(tau)).exp();
                M.fillCol(j+1,M.rangeCol(j).mul(c));
            }
            for(int n=0; n<number;n++){
                matrixList.get(n).fillCol(i,M.rangeCol(n));
            }
        }
        return matrixList;
    }

    /**
     * @author Zi
     * @date 29/09/2022
     * @description 根据蒙卡模拟的基础参数及观察日年化期限List生成价格模拟矩阵
     */
    public Matrix GenerateMonteCarloMatrix(Double S0, Double r, Double q,
                                           Double quanto, Double vol, int path,
                                           ArrayList<Double> tList, boolean isReVal) {
        Matrix M = zeros(path, tList.size() + 1);
//        M.fill(RandomType.BOX_Muller);
//        M.fill(RandomType.GAUSSIAN);
        if(!isReVal){
//        M.fill(RandomType.BOX_Muller);
//        M.fill(RandomType.GAUSSIAN);
            M.fill(new Random());
            randnMatrixs.add(new Matrix(M));
        }else{
            M.fill(randoms);
        }
        M.fillCol(0,new Array(path).fill(S0));
        Double drift = r - q - quanto - 0.5 * Math.pow(vol, 2);
        double tau;

        // 以tList为基础做循环
        for (int j = 0; j < tList.size(); j++) {
            if (j == 0) {
                tau = tList.get(j);
            } else {
                tau = tList.get(j) - tList.get(j-1);
            }
            Array c = M.rangeCol(j+1).mul(vol * Math.sqrt(tau)).add(drift * tau).exp();
            M.fillCol(j+1,M.rangeCol(j).mul(c));
        }
        return M;
    }

    public Matrix GenerateMonteCarloMatrix(Double S0, Double r, Double q,
                                           Double quanto, Double vol, int path,
                                           ArrayList<Double> tList) {
        Matrix M = zeros(path, tList.size() + 1);
//        M.fill(RandomType.BOX_Muller);
        M.fill(RandomType.GAUSSIAN);
        M.fillCol(0,new Array(path).fill(S0));
        Double drift = r - q - quanto - 0.5 * Math.pow(vol, 2);
        double tau;

        // 以tList为基础做循环
        for (int j = 0; j < tList.size(); j++) {
            if (j == 0) {
                tau = tList.get(j);
            } else {
                tau = tList.get(j) - tList.get(j-1);
            }
            Array c = M.rangeCol(j+1).mul(vol * Math.sqrt(tau)).add(drift * tau).exp();
            M.fillCol(j+1,M.rangeCol(j).mul(c));
        }
        return M.range(0, M.rows(), 1, M.cols());
    }

    /**
     * @author qianchen
     * @date 2023/8/1
     * @description 自定义年化天数
     */
    public Matrix GenerateMonteCarloMatrix(Double S0, Double r, Double q,
                                           Double quanto, Double vol, int path,
                                           ArrayList<Double> tList, Double yearFrac, boolean isReVal,
                                           boolean isRandomMatirxExist, Matrix randomMatrix) {
        Matrix M;
        if ( !isRandomMatirxExist ) {
            M = zeros(path, tList.size() + 1);
            if(!isReVal){
//            M.fill(RandomType.BOX_Muller);
//            M.fill(RandomType.GAUSSIAN);
                randoms = new Matrix(M.rows(), M.cols());
                randoms.fill(new Random());
                randnMatrixs.add(randoms);
                M.fill(randoms);
            }else {
                M.fill(randoms);
            }
        } else {
            M = randomMatrix;
        }
        M.fillCol(0,new Array(path).fill(S0));
        Double drift = r - q - quanto - 0.5 * Math.pow(vol, 2);
        double tau = 0.0;
        double expected = r - q - quanto;
        logger.debug("交易号:{} S0: {}, 波动率: {}, 无风险利率: {}, 融资率: {}",this.instrument.getInstId(), S0, vol, r, r-q);

        // 以tList为基础做循环
        for (int j = 0; j < tList.size(); j++) {
            if (j == 0) {
                tau = tList.get(0);
            } else {
                tau = tList.get(j)-tList.get(j-1);
            }
            Array c = M.rangeCol(j+1).mul(vol * Math.sqrt(tau)).add(drift * tau).exp();
            M.fillCol(j+1,M.rangeCol(j).mul(c));
        }
        return M;
    }



    /**
     * @author qianchen
     * @date 2023/9/18
     * @description 多标的根据相关系数矩阵生成模拟价格
     */
    public Matrix GenerateMonteCarloMatrix(Double S0, Double r, Double q,
                                           Double quanto, Double vol, int path,
                                           ArrayList<Double> tList, Double yearFrac,
                                           boolean isShareRandomMatrix, Matrix cholesky) {
        Matrix M = zeros(path, tList.size() + 1);
        if(!isShareRandomMatrix){
//            M.fill(RandomType.BOX_Muller);
//            M.fill(RandomType.GAUSSIAN);
            M.fill(new Random());
            randoms = M;
        } else {
            M.fill(randoms);
        }
        M.fillCol(0,new Array(path).fill(S0));
        Double drift = r - q - quanto - 0.5 * Math.pow(vol, 2);
        double tau = 1.0 / yearFrac;  // 客户客制化需求
        logger.debug("模拟使用年化期限: " + yearFrac);
        double expected = r - q - quanto;
        logger.debug("预期收益率: " + expected);
        logger.debug("drift: " + drift);
        // step0: M.fill(new Random())放到list里
        // step1: 按行取随机数组成一个新矩阵, 新矩阵*L得到, 生成所有标的的随机数矩阵(path*times)再放回list里
        // step2: 278行

        // 以tList为基础做循环
        for (int j = 0; j < tList.size(); j++) {
            Array c = M.rangeCol(j+1).mul(vol * Math.sqrt(tau)).add(drift * tau).exp();
            M.fillCol(j+1,M.rangeCol(j).mul(c));
        }
        return M;
    }

    public Matrix GenerateMonteCarloMatrix(OptionUnderlyings.OptionUnderlyingElement oneUnderlying, Schedule simuSchedule, int path,
                                           boolean isReVal) {
        double S0 = oneUnderlying.getUnderlyingPrice().getPriceByDate(qlValDate);
        double r = oneUnderlying.getRfr();
        double q = oneUnderlying.getDividend();
        double quantoAdj = oneUnderlying.getQuantoAdj();
        double vol = oneUnderlying.getVolatility();
        double dayNumber = oneUnderlying.getYearFrac();
        ArrayList<Double> tList = new ArrayList<>();
        for (int j = 0; j < simuSchedule.dates().size(); j++) {
            QLDate date = simuSchedule.date(j);
            if (date.le(qlValDate))
                continue;
//            double dt = this.dayCounter.yearFraction(qlValDate, date);
            double dt = 1.0 / 243.0;
            tList.add(dt);
        }
        // 工作日模拟
        return GenerateMonteCarloMatrix(S0, r, q, quantoAdj, vol, path, tList, dayNumber, isReVal, false, new Matrix());
        // 自然日模拟
//        return GenerateMonteCarloMatrix(S0, r, q, quantoAdj, vol, path, tList, isReVal);
    }

    /**
     * @author qianchen
     * @date 2023/9/14
     * @description 多标的价格模拟矩阵生成
     * @param underlyingsList all underlying‘ Element
     * @param cholesky Cholesky decomposition of correlation matrix (lower triangular)
     * @return dailySimulationMatrixList for all underlying
     */
    public ArrayList<Matrix> GenerateMultiMonteCarloMatrixs(List<OptionUnderlyings.OptionUnderlyingElement> underlyingsList, Schedule simuSchedule, int path,
                                                            Matrix cholesky) {
        ArrayList<Matrix> dailyMatirxList = new ArrayList<>();
        ArrayList<Double> tList = new ArrayList<>();

        // 初始化tList, 默认多标的观察日相同, 使用相同tList
        for (int j = 0; j < simuSchedule.dates().size(); j++) {
            double count = simuSchedule.date(j).sub(qlValDate);
            if ( count <= 0)
                continue;
            double dt = count / 365.0;
            tList.add(dt);
        }
        // 生成每个标的的随机数矩阵
        for (int i = 0; i < underlyingsList.size(); i++) {
            Matrix M = zeros(path, tList.size() + 1);
//            M.fill(RandomType.BOX_Muller);
//            M.fill(RandomType.GAUSSIAN);
            M.fill(new Random());
            dailyMatirxList.add(M);
        }
        // 根据相关系数矩阵按path生成具有相关系数的随机数矩阵并放回
        for (int j = 0; j < path; j++) {
            Matrix temp = new Matrix(underlyingsList.size(), tList.size() + 1);
            for (int i = 0; i < underlyingsList.size(); i++) {
                temp.fillRow(i, dailyMatirxList.get(i).rangeRow(j).mul(1.0));
            }
            Matrix newRandoms = cholesky.mul(temp); // n*n n*m = n*m
            for (int i = 0; i < underlyingsList.size(); i++) {
                dailyMatirxList.get(i).fillRow(j, newRandoms.rangeRow(i).mul(1.0));
            }
        }
        // 初始化蒙卡参数, 并生成价格模拟矩阵
        for (int n = 0; n < underlyingsList.size(); n++) {
            OptionUnderlyings.OptionUnderlyingElement oneUnderlying = underlyingsList.get(n);
            double S0 = oneUnderlying.getUnderlyingPrice().getPriceByDate(qlValDate);
            double r = oneUnderlying.getRfr();
            double q = oneUnderlying.getDividend();
            double quantoAdj = oneUnderlying.getQuantoAdj();
            double vol = oneUnderlying.getVolatility();
            double dayNumber = oneUnderlying.getYearFrac();
            Matrix St = GenerateMonteCarloMatrix(S0, r, q, quantoAdj, vol, path, tList, dayNumber, false,
                    true, dailyMatirxList.get(n));
            dailyMatirxList.set(n, St);
        }
        return dailyMatirxList;
    }


    /**
     * @author qianchen
     * @date 2023/1/6
     * @description 返回targetList各个元素在anotherList中的位置
     */
    protected ArrayList<Integer> compareListPosition(List<QLDate> targetList, List<QLDate> anotherList) {
        ArrayList<Integer> result = new ArrayList<>();
        int position = 0;
        for (int i = 0; i < targetList.size(); i++) {
            if (targetList.get(i).le(qlValDate))
                continue;  // 跳过估值日前的日期, 包括估值日
            for (int j = 0; j < anotherList.size(); j++) {
                if (anotherList.get(j).le(qlValDate) && result.size() == 0) {
                    position += 1;  // 确认估值日位置
                    continue;
                }
                if (targetList.get(i).eq(anotherList.get(j))) {
//                    logger.debug("观察日位置确认: " + targetList.get(i) + "  提取矩阵第{}列", j-position+1);
                    result.add(j - position + 1);
                    break;
                }
                if (j == anotherList.size()) {
                    throw new EngineException(EngineCodeEnums.ERROR_UNABLE_TO_EXTRACT_OBS_DATE, String.valueOf(targetList.get(i)));
                }
            }
        }
        return result;
    }

    /**
     * @author qianchen
     * @date 2023/10/7
     * @description 判断当前观察日下所有path所有日期各条款触发情况
     */
    public Matrix checkAllPathByDay(List<OptionTerms.OptionTermElement> optionTermList, Map<OptionTermType, OneForAll.OptionObsByTerm> obsByTerm, Matrix simuMatrix,
                                    OptionTerms.OptionTermElement optionTermElement, List<QLDate> accumulateObs) {
        final Matrix result = new Matrix(simuMatrix.rows(), simuMatrix.cols());
        result.fill(0);

        for (int i = 0; i < optionTermList.size(); i++) {

            // 保股期单独判断, 累积条款直接跳过
            if (optionTermList.get(i).getTerm().equals(OptionTermType.PROTECTION) || optionTermList.get(i).getTerm().equals(OptionTermType.ACCUMULATE))
                continue;

            List<QLDate> currentOriginalObs = obsByTerm.get(optionTermList.get(i).getTerm()).getObsDates();  // 当前条款的观察日
            List<QLDate> currentObs = new ArrayList<>();
            // 去除估值日前的观察日
            for (int j = 0; j < currentOriginalObs.size(); j++) {
                if (currentOriginalObs.get(j).gt(this.qlValDate)) {
                    currentObs.add(currentOriginalObs.get(j));
                }
            }
            Matrix statusMatrix = optionTermList.get(i).getStatusMatrix();  // 当前条款各天的触发状态

            // extractTargets为当前条款currentObs在累积观察日accumulateObs中的位置, 用于后续矩阵加工
            // extractTargets.size() = result.size()
            ArrayList<Integer> extractTargets = compareListPosition(currentObs, accumulateObs);
            result.checkAllPathByDay(statusMatrix, extractTargets, i + 1);

        }
        return result;
    }


    /**
     * @author qianchen
     * @date 2023/7/11
     * @description 找到比估值日大的最小的观察日
     */
    protected int calculateValDatePosition(List<QLDate> obsDates) {
        int result = 0;
        for (int i = 0; i < obsDates.size(); i++) {
            if (obsDates.get(i).gt(this.qlValDate)) {
                result = i;
                break;
            }
        }
        return result;
    }

    /**
     * @author Zi
     * @date 29/09/2022
     * @description 根据蒙卡模拟的基础参数生成价格模拟矩阵
     */
    public Matrix GenerateMonteCarloMatrix(Double S0, Double r, Double q,
                                           Double quanto, Double vol, Double T,
                                           int path, int step) {
        ArrayList<Double> tList = new ArrayList<>();
        double dt =  T/step, t =0.0;
        for (int i =0; i< step; i++){
            tList.add(t + dt);
        }
        return GenerateMonteCarloMatrix(S0, r, q, quanto, vol, path, tList, false);
    }

    /**
     * @author Zi
     * @date 11/08/2022
     * @description 计算两数组之间的相关性系数
     */
    public Double calculateCorrelation(Array a, Array b){
        QL.require(a.size()==b.size(),"correlation array size must be same");
        double numerator=0.0, denominator, xMean, yMean, xCov=0.0, yCov=0.0;
        int size = a.size();
        xMean = a.accumulate()/size;
        yMean = b.accumulate()/size;
        for (int i =0 ; i<size; i++){
            numerator += (a.get(i)-xMean)*(b.get(i)-yMean);
            xCov += Math.pow(a.get(i)-xMean,2);
            yCov += Math.pow(b.get(i)-yMean,2);
        }
        denominator = Math.sqrt(xCov * yCov);
        return numerator/denominator;
    }

    /**
     * @author Zi
     * @date 11/08/2022
     * @description 计算quanto调整中标的资产价格和汇率价格间的相关性系数
     * @description input输入数据中的股票价格与汇率均为从估值日开始的倒序数组
     */
    public Double calculateQuantoCorrelation(Array a, Array b){
        QL.require(a.size()==b.size(),"correlation array size must be same");
        Array x = new Array(a.size()-1), y = new Array(b.size()-1);
        for(int i=0; i<a.size()-1; i++){
            x.set(i,a.get(i)/a.get(i+1));
            y.set(i,b.get(i)/b.get(i+1));
        }
        double rho = calculateCorrelation(x,y);
        return rho;
    }

    /**
     * @author Zi
     * @date 11/08/2022
     * @description 计算股票年化波动率
     */
    protected double calculateVolatility(QLDate valDate, double dayNumber, Price price, Calendar calendar) {
        double vol;
        List<Double> ri = new ArrayList<Double>();
        //用ri存放股价和上一交易日股价的自然对数
        for (int i = 0; i < dayNumber; ) {
            QLDate datei = valDate.sub(i);
            while (!calendar.isBusinessDay(datei)) {
                i++;
                datei = valDate.sub(i);
            }
            if (++i > dayNumber + 1) {
                break;
            }
            QLDate dateii = valDate.sub(i);
            while (!calendar.isBusinessDay(dateii)) {
                i++;
                dateii = valDate.sub(i);
            }
            //目前为止dateii是datei的前一个交易日
            double s=0.0, ss=0.01;
            try{
                s = price.getPriceByDate(datei);
                ss = price.getPriceByDate(dateii);
                double ln = Math.log(s / ss);
                ri.add(ln);
//                logger.debug("使用日期: {}/{}, 对数收益率为{}", datei, dateii, format.format(ln));
            }catch (EngineException e){
                if(e.getCode()== EngineCodeEnums.ERROR_THIS_CODE_NO_PRICE){
                    logger.warn("交易号:{},该日股票停牌或未上市: {}/{}",this.instrument.getInstId(), datei, dateii);
                    i++;
                }
            }
//            double ln = Math.log(s / ss);
//            ri.add(ln);
        }
        //calculate mean
        double sum = 0.0;
        for (int j = 0; j < ri.size(); j++) {
            sum += ri.get(j);
        }
        double mean = sum / ri.size();
        //calculate std dev
        double squre = 0.0;
        for (int k = 0; k < ri.size(); k++) {
            double diff = ri.get(k) - mean;
            squre += Math.pow(diff, 2);
        }
        double std_dev = Math.sqrt(squre / ri.size());
        vol = std_dev * Math.sqrt(250);
        return vol;
    }

    /**
     * @author Zi
     * @date 26/08/2022
     * @description 快速生成三对角矩阵
     * @source scipy.sparse.diags([l,c,u],[-1,0,1])
     */
    protected Matrix diags(Array l, Array c,Array u){
        Matrix A = new Matrix(c.size(),c.size());
        for(int i=0; i<c.size(); i++){
            if(i!=c.size()-1){
                A.set(i+1,i,l.get(i));
                A.set(i,i+1,u.get(i+1));
            }
            A.set(i,i,c.get(i));
        }
        return A;
    }

    /**
     * @author Zi
     * @date 26/08/2022
     * @description 快速生成x行x列的对角矩阵,矩阵对角线赋值均为1
     */
    protected Matrix eyes(int x){
        Matrix A = new Matrix(x, x);
        for(int i=0; i<x; i++){
            A.set(i,i,1);
        }
        return A;
    }

    /**
     * @author Zi
     * @date 26/08/2022
     * @description 快速生成m行n列的零矩阵,矩阵对角线赋值均为1
     */
    protected Matrix zeros(int m, int n){
        Matrix A = new Matrix(m, n);
        A.fill(0);
        return A;
    }


    /**
     * @author qianchen
     * @date 2023/1/3
     * @description 计算标的股票和外汇价格T日相关系数
     */
    protected double calcRho(Price stockPrices, Price fxSpots, Calendar calendar, QLDate valDate){
        double rho = 0.0;
        List<Double> stockPriceList = new ArrayList<Double>();
        List<Double> fxSpotList = new ArrayList<Double>();
        for(int i = 100; i > 0; i--){  // 实际生产环境i应取365
            QLDate d = valDate.sub(i);
            if(!calendar.isBusinessDay(d)){
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
        rho = calculateQuantoCorrelation(x,y);
        return rho;
    }

    /**
     * @author qianchen
     * @date 2023/9/13
     * @description 判断多标的信息的联合日历
     */
    protected boolean allCalendarsIsBusinessDay(List<OptionUnderlyings.OptionUnderlyingElement> underlyings, QLDate date) {
        int count = 0;
        for (OptionUnderlyings.OptionUnderlyingElement oneUnderlying : underlyings) {
            Calendar calendar = rds.getCalendars().getCalendar(oneUnderlying.getCalendarName());
            int temp = calendar.isBusinessDay(date) ? 0 : 1;
            count += temp;
        }
        return count == 0;
    }


    /**
     * @author qianchen
     * @date 2023/9/12
     * @description 初始化多标的250天价格收益率
     */
    protected double[][] prepareForPearsonsCorrelation(QLDate valDate, List<OptionUnderlyings.OptionUnderlyingElement> underlyings) {
        List<ArrayList> prices = new ArrayList<>();
        double[][] data = new double[underlyings.size()][];

        for (int j = 0; j < underlyings.size(); j++) {
            prices.add(new ArrayList<Double>());
        }

        DATELOOP: for (int i = 0; i < 500; i++) {
            QLDate datei = valDate.sub(i);

            // 判断该前后日期是否为交易日
            while (!allCalendarsIsBusinessDay(underlyings, datei)) {
                i++;
                datei = valDate.sub(i);
            }

            QLDate dateii = valDate.sub(i+1);
            while (!allCalendarsIsBusinessDay(underlyings, dateii)) {
                i++;
                dateii = valDate.sub(i+1);
            }

            // 先check所有标的当前计算日期是否都有数据, 没有数据跳过该日
            for (int j = 0; j < underlyings.size(); j++) {
                OptionUnderlyings.OptionUnderlyingElement oneUnderlying = underlyings.get(j);
                Price price = oneUnderlying.getUnderlyingPrice();
                double s = 0.0, ss = 0.01;
                s = price.getPriceByDate(datei);
                ss = price.getPriceByDate(dateii);
                if (! price.getFlagByDate(datei) && price.getFlagByDate(dateii) ){
                    logger.warn("交易号:{},该日股票{}停牌或未上市: [{}]-[{}]",this.instrument.getInstId(), price.getCode(), datei, dateii);
                    continue DATELOOP;
                }
            }

            // 都有数据后开始计算对数收益率
            for (int j = 0; j < underlyings.size(); j++) {
                OptionUnderlyings.OptionUnderlyingElement oneUnderlying = underlyings.get(j);
                ArrayList<Double> onePrice = prices.get(j);
                Price price = oneUnderlying.getUnderlyingPrice();
                double s = price.getPriceByDate(datei);
                double ss = price.getPriceByDate(dateii);
                double ln = Math.log( s / ss);
                onePrice.add(ln);
            }

            if (prices.get(0).size() == 250)
                break;

        }

        if (prices.get(0).size() < 20) {
            logger.warn("{},该笔交易标的上市日期不足20天" + this.instrument.getInstId());
        }

        for (int i = 0; i < prices.size(); i++) {
            ArrayList<Double> value = prices.get(i);
            data[i] = value.stream().mapToDouble(v -> v.doubleValue()).toArray();
        }

        return data;
    }

    /**
     * @author qianchen
     * @date 2023/9/13
     * @description 生成Pearson相关系数矩阵
     */
    protected RealMatrix GenerateCorrelationMatrix(double[][] data) {
        RealMatrix matrix = new BlockRealMatrix(data).transpose();
        PearsonsCorrelation correlation = new PearsonsCorrelation();
        return correlation.computeCorrelationMatrix(matrix);
    }

    public int calculateBusinessDays(QLDate d1, QLDate d2) {
        return 1;
    }



}
