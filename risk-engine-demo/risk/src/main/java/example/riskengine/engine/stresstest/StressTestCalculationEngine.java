package example.riskengine.engine.stresstest;

import example.riskengine.data.Cache;
import example.riskengine.data.CacheCfg;
import example.riskengine.data.rds.bo.IndicatorStressTest;
import example.riskengine.data.rds.bo.ScenarioCust;
import example.riskengine.data.rds.bo.ScenarioHist;
import example.riskengine.data.res.Res;
import example.riskengine.data.res.entity.ValResPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * @author qianchen
 * @date 2024/4/9
 * @Description 计算传入投组及投组下所有节点的所有压力测试值
 */
@Component
@Scope("prototype")
public class StressTestCalculationEngine implements Callable<String> {

    /**
     * 日志
     */
    private static final Logger logger = LoggerFactory.getLogger(StressTestCalculationEngine.class);

    private CacheCfg cacheCfg;

    private Cache cache;

    private Res res;

    /**
     * 要计算的压力测试指标
     */
    private IndicatorStressTest indicatorStressTest;



    @Override
    public String call() throws Exception {
        try {
            //所有的历史情景
            Map<String, ScenarioHist> cfgMrScenarioHist = cache.getRds().getCfgMrScenarioHist();
            //获取自定义情景
            Map<String, ScenarioCust> cfgMrScenarioCust = cache.getRds().getCfgMrScenarioCust();
            //获取所有情景头寸估值结果
            Map<String, List<ValResPosition>> positionResultListMap = res.getPositionResultListMap();
            //获取原始估值 0号估值结果,默认是101，get("101");
            List<ValResPosition> zeroPositonResList = positionResultListMap.get(cacheCfg.getPvSet().iterator().next()+"");
            if(zeroPositonResList == null){
                throw new RuntimeException("零号估值结果集为空，请注意；！！！");
            }
            //压力情景估值结果 定义
            List<ValResPosition> secePositonResList = null;
            //一个压力测试指标，依赖一个压力情景
            if("HIST".equalsIgnoreCase(indicatorStressTest.getMrSTType().getType())){
                ScenarioHist scenarioHist = cfgMrScenarioHist.get(indicatorStressTest.getvScenarioIds());
                Set<String> dateChangeString = scenarioHist.getDateChangeString();
//            针对压力情景，日期变动结果 只有一个结果值返回
                if(dateChangeString.size() != 1){
                    throw new RuntimeException("压力测试情景："
                            +scenarioHist.getvScenarioId() +" ,getDateChangeString结果不为1，有误，请检查！");
                }
                String next = dateChangeString.iterator().next();
                //历史情景 是 情景ID|refEndDate|refStartDate
                secePositonResList = positionResultListMap.get(scenarioHist.getvScenarioId()+"|"+next);
            }else {
                ScenarioCust scenarioCust = cfgMrScenarioCust.get(indicatorStressTest.getvScenarioIds());
                //自定义情景 情景ID|情景ID
                secePositonResList = positionResultListMap.get(scenarioCust.getvScenarioId()+"|"+scenarioCust.getvScenarioId());
            }

            //判断是否获取到 请估值头寸结果集；
            if(secePositonResList == null){
                throw new RuntimeException("压力测试情景："
                        +indicatorStressTest.getvScenarioIds() + " ,未获取到情景头寸估值结果集，请检查！");
            }

            //获取情景估值,key为 应该为头寸编号，当前模型下 是估值日+交易号tradeNo；
            //该指标下，key为 交易号
            //获取情景估值
            Map<String,ValResPosition>  scenarioPositionMap = getPositionMap(secePositonResList);

            //遍历0号估值头寸结果集
            for(ValResPosition temp:zeroPositonResList){
                ValResPosition position = scenarioPositionMap.get(temp.getvTradeNo());
                if(position != null){
                    ValResPosition resR = new ValResPosition(position, indicatorStressTest.nIndicatorId);
                    resR.setnResult(position.getnResult()-temp.getnResult());
                    //保存到res
                    res.addPositionResult(indicatorStressTest.getnIndicatorId()+"",resR);
                }else{
                    logger.warn("压力测试计量，指标ID "+indicatorStressTest.getnIndicatorId()+"计量过程中，交易编号："
                            +temp.getvTradeNo()+",无情景估值结果！请注意！");
                }
            }
        }catch (Exception e){
            logger.error("压力测试指标指计算异常，指标ID为："+indicatorStressTest.getnIndicatorId()+", "+e.getMessage(),e);
        }
        return "success";
    }

    private Map<String, ValResPosition> getPositionMap(List<ValResPosition> zeroPositonResList) {
        HashMap<String, ValResPosition> map = new HashMap<>();
        for(ValResPosition temp:zeroPositonResList){
            map.put(temp.getvTradeNo(),temp);
        }
        return map;
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

    public IndicatorStressTest getIndicatorStressTest() {
        return indicatorStressTest;
    }

    public void setIndicatorStressTest(IndicatorStressTest indicatorStressTest) {
        this.indicatorStressTest = indicatorStressTest;
    }

}
