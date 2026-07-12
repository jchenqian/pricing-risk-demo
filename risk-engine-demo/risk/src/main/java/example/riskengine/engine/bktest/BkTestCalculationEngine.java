package example.riskengine.engine.bktest;

import example.riskengine.common.bases.time.BusinessDayConvention;
import example.riskengine.common.bases.time.Calendar;
import example.riskengine.common.bases.time.QLDate;
import example.riskengine.common.bases.time.TimeUnit;
import example.riskengine.data.Cache;
import example.riskengine.data.CacheCfg;
import example.riskengine.data.rds.bo.IndicatorBkTest;
import example.riskengine.data.res.Res;
import example.riskengine.data.res.entity.ValResPortfolio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * @author qianchen
 * @date 2024/4/29
 * @Description 返回检验计量引擎
 */
@Component
@Scope("prototype")
public class BkTestCalculationEngine implements Callable<String> {

    /**
     * 日志
     */
    private static final Logger logger = LoggerFactory.getLogger(BkTestCalculationEngine.class);

    private CacheCfg cacheCfg;

    private Cache cache;

    private Res res;

    /**
     * 要计算的VaR，是什么VaR
     */
    private IndicatorBkTest indicatorBkTest;


//    理论损益	当前批量日期
//    VaR	上一批量日期
//    返回检验即 比较损益跟VaR值，看看损益是否突破了VaR值
    //理论损益指标

    @Override
    public String call() throws Exception {

        try{


            //获取需要汇总的投组,key 为每一个需要汇总的节点，value是这个key下面 所有的叶子节点
            Map<String, Set<String>> portfolioLeafMap = cache.getRds().getPortfolioLeafMap();
//        Calendar cnyIb = cache.getRds().getCalendars().getCalendar("CNY IB");//获取日历

            Date valDate = cacheCfg.getValDate();
//        QLDate qlDate = new QLDate(valDate);
            //取上一个工作日，当前估值日，往前回溯一天
//        QLDate advance = cnyIb.advance(qlDate, 1, TimeUnit.Days, BusinessDayConvention.Preceding, true);
            Date previousWorkDay = cacheCfg.getPreviousWorkDay();
            Map<String, ValResPortfolio> varPortfolioResult =
                    res.getHistoryPortfolioResult(String.valueOf(indicatorBkTest.nVaRIndicatorId), previousWorkDay);
            Map<String, ValResPortfolio> pnlPortfolioResult = res.getPortfolioResult(String.valueOf(indicatorBkTest.nPnlIndicatorId));

            //遍历key
            for (String portfolioId : portfolioLeafMap.keySet()) {
                //获取var值
                ValResPortfolio valResPortfolio = varPortfolioResult.get(portfolioId);
                //获取理论损益值
                ValResPortfolio pnlResPortfolio = pnlPortfolioResult.get(portfolioId);

                if (valResPortfolio != null && pnlResPortfolio != null) {
                    ValResPortfolio resultPortfolio = new ValResPortfolio(valResPortfolio);
                    resultPortfolio.setnIndicatorId(getIndicatorBkTest().nIndicatorId);
                    resultPortfolio.setvPortfolioId(portfolioId);
                    resultPortfolio.setdDataDt(cacheCfg.getValDate());
                    //var值 代表损失，保存结果的时候，加了负号，故而 给理论损益加负号
                    if (valResPortfolio.getnResult() < -pnlResPortfolio.getnResult()) {
//                   resultPortfolio.setnResult(1d);//突破
//                   突破值保存
                        resultPortfolio.setnResult(-pnlResPortfolio.getnResult() - valResPortfolio.getnResult());
                    } else {
                        resultPortfolio.setnResult(0d);
                    }
                    res.addPortfolioResult(String.valueOf(indicatorBkTest.nIndicatorId), resultPortfolio);
                }
            }
        }catch (Exception e){
            logger.error("返回检验指计算异常，指标ID为："+indicatorBkTest.getnIndicatorId()+", "+e.getMessage(),e);
        }
        return "success";
    }


    @Autowired
    public void setCacheCfg(CacheCfg cacheCfg) {
        this.cacheCfg = cacheCfg;
    }

    @Autowired
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    @Autowired
    public void setRes(Res res) {
        this.res = res;
    }

    public IndicatorBkTest getIndicatorBkTest() {
        return indicatorBkTest;
    }

    public void setIndicatorBkTest(IndicatorBkTest indicatorBkTest) {
        this.indicatorBkTest = indicatorBkTest;
    }
}
