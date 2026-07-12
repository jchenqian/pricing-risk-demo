package example.riskengine.engine.valuation;

import example.riskengine.common.bases.time.BusinessDayConvention;
import example.riskengine.common.bases.time.Calendar;
import example.riskengine.common.bases.time.Period;
import example.riskengine.common.bases.time.QLDate;
import example.riskengine.common.enums.rds.MrScenarioVaRType;
import example.riskengine.common.enums.tds.UserSelectedModelType;
import example.riskengine.common.exceptions.EngineCodeEnums;
import example.riskengine.common.exceptions.EngineException;
import example.riskengine.common.lang.DateUtils;
import example.riskengine.data.Cache;
import example.riskengine.data.CacheCfg;
import example.riskengine.data.mds.Mds;
import example.riskengine.data.rds.bo.Calendars;
import example.riskengine.data.rds.bo.ScenarioHist;
import example.riskengine.data.rds.bo.ScenarioVaR;
import example.riskengine.data.res.entity.ValResPosition;
import example.riskengine.data.tds.bo.Instrument;
import example.riskengine.engine.CalcTaskGroup;
import example.riskengine.engine.InstValEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author qianchen
 * @date 2024/3/31
 * @Description 情景估值任务组
 */
@Component
public class ScenarioValuationTaskGroup implements CalcTaskGroup {

    /**
     * 日志
     */
    private static final Logger logger = LoggerFactory.getLogger(ScenarioValuationTaskGroup.class);
    private Cache cache;
    private CacheCfg cacheCfg;
    private ScenarioValuationTaskManager scenarioValuationTaskManager;
    private ApplicationContext applicationContext;
    @Value("${task.mc.batch-size}")
    private int taskMcBatchSize;

    /**
     * 上一个工作日，当进行损益归因，理论损益计算重估值时，上一个工作日将不为空，并且计算范围 是以
     * 上一个工作日持仓范围，作为计算范围     *
     */
    private Date previousWorkDay;
    /**
     * 与上一工作日联动，它应该由  情景ID|估值日|上一工作日 拼接而成
     * 如 ‘301|2024-01-24|2024-01-23’
     */
    private String previousScenarioIndex;

    /**
     * @author 牧羊人 Michael Cai
     * @date 2023/3/31
     * @description 引擎计算
     */
    @Override
    public void calc() {
        // 获取估值日交易编号列表
        Date valDate = cacheCfg.getValDate();

        List<Instrument> instList = cache.getTds().getInstruments(new QLDate(valDate));
        //获取此次估值范围
//        Map<String, CalculateScope> calculateScopeMap = cache.getRds().getCalculateScopeMap();

        //获取所有需要情景估值的情景索引ID
        Set<String> allScenarioIndex = getAllScenarisoIndex(valDate);

        Map<String, ScenarioVaR> cfgMrScenarioVaR = cache.getRds().getCfgMrScenarioVaR();

        Set<String> mcScenarioSet = new HashSet<>();


        //遍历所有需要估值的交易，每笔交易针对每个情景，都需要做一遍估值；
        Iterator<Instrument> iterator = instList.iterator();
        while (iterator.hasNext()) {
            Instrument instrument = iterator.next();
            //判断是否在计算范围内，不是直接跳过
//            CalculateScope calculateScope = calculateScopeMap.get(instrument.getInstId());
//            if (calculateScope == null || (calculateScope != null && !calculateScope.isvCalculateFlag()))
//                continue;
            try {
                // 金融工具类型
                String instType = instrument.getInstType().getType();
                //标准估值模型
                String valModelType = instrument.getValModelType().getType();
                //用户自定义选择估值模型
                String userSelectedModelType = instrument.getUserSelectedModelType().getType();

                for (String scenarioIndex : allScenarioIndex) {

                    //蒙卡VaR不计算蒙卡估值
                    //当情景为蒙卡情景时,应该跳过蒙卡估值（MONTE_CARLO_USERMODEL）
                    //蒙卡情景会非常多，一个蒙卡VaR会有，约1万个的蒙卡模拟情景，一个蒙卡估值，约有一万次的估值计算
                    //故而，当是蒙卡情景时，选择跳过蒙卡估值，不进行计算；
                    if (UserSelectedModelType.MONTE_CARLO_USERMODEL.getType().equalsIgnoreCase(userSelectedModelType)
                            && scenarioIndex.contains("|") && scenarioIndex.split("\\|").length == 3) {
                        continue;
                    }

                    String[] elements = scenarioIndex.split("\\|");
                    ScenarioVaR scenarioVaR = cfgMrScenarioVaR.get(elements[0]);
                    if (scenarioVaR != null && scenarioVaR.getMrScenarioVaRType().equals(MrScenarioVaRType.MCVaR)) {
                        //如果是蒙卡情景
                        mcScenarioSet.add(scenarioIndex);
                    } else {
                        //非蒙卡情景
                        Mds mds = cache.getScenarioMds().getMdsByScenarioIdAndIndexId(scenarioIndex);
                        String beanName = String.format("%s_%s_%s", instType, valModelType, userSelectedModelType);

                        String businessType = instrument.getBusinessType();
                        //如过是B0101产品，自定义期权模型，走自定义Python
                        if (businessType.startsWith("B0101")) {
                            beanName = "OPTION_CUST_PYTHON_MODEL";
                        }
                        InstValEngine engine = applicationContext.getBean(beanName, InstValEngine.class);
                        engine.setValDate(valDate);// 设置估值日期
                        engine.setCacheCfg(cacheCfg);// 设置缓存估值参数
                        engine.setInstrument(instrument);// 设置待估值的交易数据
                        engine.setRes(cache.getRes());// 设置结果数据存放位置
                        engine.setRds(cache.getRds());// 设置估值需要的参数数据
                        engine.setTds(cache.getTds());
                        engine.setMds(mds);// 设置估值需要的市场数据
                        /**
                         * 当前情景估值对应的索引编码
                         * 风险计量指标用
                         * 当是历史情景时，历史VaR，scenarioIndex = vScenarioId+|+dateChangeScope; dateChangeScope是由 历史情景的结束日|开始日 拼接而成， refEndDate|refStartDate
                         * 当是蒙卡VaR情景，scenarioIndex = vScenarioId|dateChangeScope|模拟位置，模拟位置是0.1.2.3...模拟次数-1
                         * 当时自定义情景时，scenarioIndex = vScenarioId+|+vScenarioId;
                         */
                        engine.setIndexId(scenarioIndex);
                        engine.setIndicator(cache.getRds().getCfgMrIndicator().get(cacheCfg.getPvSet().iterator().next()));//估值 指标
                        scenarioValuationTaskManager.addTask(engine);
                    }
                }

            } catch (NoSuchBeanDefinitionException e) {
                EngineException ne = new EngineException(e, EngineCodeEnums.ERROR_UNKNOW_INST_VALMODEL, instrument.getInstId());
                logger.error(ne.getMessage(), ne);
            } catch (Exception e) {
                EngineException ne = new EngineException(e, EngineCodeEnums.ERROR_CREATE_INST_ENG_FAIL, instrument.getInstId());
                logger.error(ne.getMessage(), ne);
            }
        }

        //如果上一个工作日不为空，需要做一遍T-1持仓的，T日的情景估值
        Set<String> set4Rtpl = cache.getRds().getInUsedHistScenarioSet4Rtpl();
        if (set4Rtpl != null && set4Rtpl.size() > 0) {
            for (String scenarioId : set4Rtpl) {
                previousWorkDayValuation(scenarioId);
            }
        }

        //进行蒙卡估值
        if (mcScenarioSet.size() > 0) {
            for (String scenarioIndex : mcScenarioSet) {
                String[] elements = scenarioIndex.split("\\|");
                ScenarioVaR scenarioVaR = cfgMrScenarioVaR.get(elements[0]);
                //如果是蒙卡情景
                int mcTimes = scenarioVaR.getMcTimes();
                long startTime = 0l;
                int remi = 0;
                for (int i = 0; i < mcTimes; i++) {
                    Mds mds = cache.getScenarioMds().getMdsByScenarioIdAndIndexId(scenarioIndex + "|" + i);
                    if (mds == null)
                        continue;
                    //遍历所有需要估值的交易，每笔交易针对每个情景，都需要做一遍估值；
                    Iterator<Instrument> iterator4mc = instList.iterator();

                    while (iterator4mc.hasNext()) {
                        Instrument instrument = iterator4mc.next();
                        // 金融工具类型
                        String instType = instrument.getInstType().getType();
                        //标准估值模型
                        String valModelType = instrument.getValModelType().getType();
                        //用户自定义选择估值模型
                        String userSelectedModelType = instrument.getUserSelectedModelType().getType();
                        //蒙卡VaR不计算蒙卡估值
                        //当情景为蒙卡情景时,应该跳过蒙卡估值（MONTE_CARLO_USERMODEL）
                        //蒙卡情景会非常多，一个蒙卡VaR会有，约1万个的蒙卡模拟情景，一个蒙卡估值，约有一万次的估值计算
                        //故而，当是蒙卡情景时，选择跳过蒙卡估值，不进行计算；
                        if (UserSelectedModelType.MONTE_CARLO_USERMODEL.getType().equalsIgnoreCase(userSelectedModelType)) {
                            continue;
                        }
                        String beanName = String.format("%s_%s_%s", instType, valModelType, userSelectedModelType);
                        String businessType = instrument.getBusinessType();
                        //如过是B0101产品，自定义期权模型，走自定义Python
                        if (businessType.startsWith("B0101")) {
                            beanName = "OPTION_CUST_PYTHON_MODEL";
                        }
                        InstValEngine engine = applicationContext.getBean(beanName, InstValEngine.class);
                        engine.setValDate(valDate);// 设置估值日期
                        engine.setCacheCfg(cacheCfg);// 设置缓存估值参数
                        engine.setInstrument(instrument);// 设置待估值的交易数据
                        engine.setRes(cache.getRes());// 设置结果数据存放位置
                        engine.setRds(cache.getRds());// 设置估值需要的参数数据
                        engine.setTds(cache.getTds());
                        engine.setMds(mds);// 设置估值需要的市场数据
                        /**
                         * 当前情景估值对应的索引编码
                         * 风险计量指标用
                         * 当是蒙卡VaR情景，scenarioIndex = vScenarioId|dateChangeScope|模拟位置，模拟位置是0.1.2.3...模拟次数-1
                         */
                        engine.setIndexId(scenarioIndex + "|" + i);
                        engine.setIndicator(cache.getRds().getCfgMrIndicator().get(cacheCfg.getPvSet().iterator().next()));//估值 指标
                        scenarioValuationTaskManager.addTask(engine);
                    }

                    // 每taskMcBatchSize次先计算一波。剩余的走默认逻辑
                    if ((i + 1) % taskMcBatchSize == 0) {
                        startTime = System.currentTimeMillis();
                        logger.info("蒙卡估值第{}波开始", (i + 1) / taskMcBatchSize);
                        scenarioValuationTaskManager.checkTask(false);
                        for (int j = remi; j < i; j++) {
                            cache.getScenarioMds().removeScenarioMds(scenarioIndex + "|" + j);
                        }
                        logger.info("蒙卡估值第{}波结束耗时{}毫秒", (i + 1) / taskMcBatchSize, System.currentTimeMillis() - startTime);
                        remi = i;
                    }
                }
            }
        }
    }

    /**
     * T-1持仓的，T日的情景估值
     */
    private void previousWorkDayValuation(String scenarioId) {

        Map<String, ScenarioHist> cfgMrScenarioHist = cache.getRds().getCfgMrScenarioHist();
        //获取债券点差指标，905
        Map<String, ValResPosition> zSpreadHistoryPositionResult
                = cache.getRes().getHistoryPositionResult("905", cacheCfg.getPreviousWorkDay());
        if (cfgMrScenarioHist != null) {
            ScenarioHist scenarioHist = cfgMrScenarioHist.get(scenarioId);
            Set<String> dateChangeString = scenarioHist.getDateChangeString();
            if (dateChangeString.size() == 1) {
                String dateChangeStr = dateChangeString.iterator().next();
                String[] dateArr = dateChangeStr.split("\\|");
                Date previousWorkDay = DateUtils.parseDate(dateArr[0]);
                //获取上一工作日持仓
                List<Instrument> instList =
                        cache.getTds().getInstruments(new QLDate(previousWorkDay));
                if (instList != null && instList.size() > 0) {
                    //遍历所有需要估值的交易，每笔交易针对每个情景，都需要做一遍估值；
                    Iterator<Instrument> iterator = instList.iterator();
                    while (iterator.hasNext()) {
                        Instrument instrument = iterator.next();
                        try {
                            // 金融工具类型
                            String instType = instrument.getInstType().getType();
                            //标准估值模型
                            String valModelType = instrument.getValModelType().getType();
                            //用户自定义选择估值模型
                            String userSelectedModelType = instrument.getUserSelectedModelType().getType();
                            //历史情景估值，债券需要历史点差
                            if (zSpreadHistoryPositionResult != null && zSpreadHistoryPositionResult.get(instrument.getInstId()) != null) {
                                instrument.setSpread(zSpreadHistoryPositionResult.get(instrument.getInstId()).getnResult());
                            }

                            Mds mds = cache.getScenarioMds().getMdsByScenarioIdAndIndexId(scenarioId + "|" + dateChangeStr);
                            String beanName = String.format("%s_%s_%s", instType, valModelType, userSelectedModelType);
                            String businessType = instrument.getBusinessType();
                            //如过是B010101产品，自定义期权模型，走自定义Python
                            if (businessType.startsWith("B0101")) {
                                beanName = "OPTION_CUST_PYTHON_MODEL";
                            }
                            InstValEngine engine = applicationContext.getBean(beanName, InstValEngine.class);
                            engine.setValDate(cacheCfg.getPreviousWorkDay());// 设置估值日期
                            engine.setCacheCfg(cacheCfg);// 设置缓存估值参数
                            engine.setInstrument(instrument);// 设置待估值的交易数据
                            engine.setRes(cache.getRes());// 设置结果数据存放位置
                            engine.setRds(cache.getRds());// 设置估值需要的参数数据
                            engine.setTds(cache.getTds());
                            engine.setMds(mds);// 设置估值需要的市场数据 用的就是T日的市场数据
                            engine.setIndexId(scenarioId + "|" + dateChangeStr);
                            engine.setIndicator(cache.getRds().getCfgMrIndicator().get(cacheCfg.getPvSet().iterator().next()));//估值 指标
                            scenarioValuationTaskManager.addTask(engine);

                        } catch (NoSuchBeanDefinitionException e) {
                            EngineException ne = new EngineException(e, EngineCodeEnums.ERROR_UNKNOW_INST_VALMODEL, instrument.getInstId());
                            logger.error(ne.getMessage(), ne);
                        } catch (Exception e) {
                            EngineException ne = new EngineException(e, EngineCodeEnums.ERROR_CREATE_INST_ENG_FAIL, instrument.getInstId());
                            logger.error(ne.getMessage(), ne);
                        }
                    }
                }
            }
        }

    }

    @Autowired
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    @Autowired
    public void setCacheCfg(CacheCfg cacheCfg) {
        this.cacheCfg = cacheCfg;
    }

    @Autowired
    public void setVarRevalTaskManager(ScenarioValuationTaskManager scenarioValuationTaskManager) {
        this.scenarioValuationTaskManager = scenarioValuationTaskManager;
    }

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 当前估值对应的索引编码
     * 风险计量指标用
     * 当是历史情景时，历史VaR，scenarioIndex = vScenarioId+|+dateChangeScope; dateChangeScope是由 历史情景的结束日|开始日 拼接而成， refEndDate|refStartDate
     * 当是蒙卡VaR情景，scenarioIndex = vScenarioId|dateChangeScope|模拟位置，模拟位置是0.1.2.3...模拟次数-1
     * 当时自定义情景时，scenarioIndex = vScenarioId+|+vScenarioId;
     */
    private Set<String> getAllScenarisoIndex(Date valDate) {
        //当前批次任务使用到的历史情景
        Set<String> inUsedHistScenarioSet = cache.getRds().getInUsedHistScenarioSet();
        //所有的VaR情景
        Map<String, ScenarioVaR> cfgMrScenarioVaR = cache.getRds().getCfgMrScenarioVaR();
        //所有的历史情景
        Map<String, ScenarioHist> cfgMrScenarioHist = cache.getRds().getCfgMrScenarioHist();
        //当前批次任务使用到的自定义情景
        Set<String> inUsedCustScenarioSet = cache.getRds().getInUsedCustScenarioSet();
        Calendars calendars = cache.getRds().getCalendars();
        Calendar cnhIb = calendars.getCalendar("CNY IB");

        Set<String> resultSet = new HashSet<>();

        //将历史情景的 所有情景变动区间做个拼接，情景ID+“|” + 区间变动日期字符串
        //拼接完成通常为 "情景ID|refEndDate|refStartDate" 日期格式为 yyyy-MM-dd
        //VaR拼接完成通常为 "情景ID|refEndDate|refStartDate|模拟位置" 日期格式为 yyyy-MM-dd,模拟位置为：0.1.2.3... 模拟次数-1
        for (String scenarioId : inUsedHistScenarioSet) {
            ScenarioHist scenarioHist = cfgMrScenarioHist.get(scenarioId);
            ScenarioVaR scenarioVaR = cfgMrScenarioVaR.get(scenarioId);
            Set<String> indexSet = null;
            if (scenarioHist != null) {
                scenarioHist.setValDate(new QLDate(valDate));
                //获取历史情景 两个变动日期字符串
                indexSet = scenarioHist.getDateChangeString();
                //当size等于0时，说明是额外T-1日持仓的情景估值，需要特殊处理
                if (indexSet.size() == 0) {
                    setPreviousWordDay(valDate, cnhIb, scenarioHist);
                }
            }
            if (scenarioVaR != null) {
                scenarioVaR.setValDate(new QLDate(valDate));
                //获取历史情景 两个变动日期字符串
                indexSet = scenarioVaR.getDateChangeString(calendars);
            }
            if (indexSet != null) {
                Iterator<String> iterator = indexSet.iterator();
                while (iterator.hasNext()) {
                    resultSet.add(scenarioId + "|" + iterator.next());
                }
            }
        }

        //自定义情景 情景数据索引ID为 两个情景ID的拼接 通常为 情景ID|情景ID
        Iterator<String> iterator = inUsedCustScenarioSet.iterator();
        while (iterator.hasNext()) {
            String vScenarioId = iterator.next();
            resultSet.add(vScenarioId + "|" + vScenarioId);
        }
        return resultSet;
    }

    /**
     * 设置上一个工作日
     *
     * @param valDate
     * @param cnhIb
     * @param scenarioHist
     */
    private void setPreviousWordDay(Date valDate, Calendar cnhIb, ScenarioHist scenarioHist) {
        String lookBackperiod = scenarioHist.getvLookBackperiod();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        QLDate qlDate = new QLDate(valDate);
//                    获取上一工作日
        QLDate subDate = qlDate.sub(Period.parse(lookBackperiod));
        QLDate adjust = cnhIb.adjust(subDate, BusinessDayConvention.Preceding);
        previousWorkDay = adjust.isoDate();
        previousScenarioIndex = scenarioHist.getvScenarioId() + "|" + format.format(valDate) + "|" + format.format(adjust.isoDate());
    }

}



