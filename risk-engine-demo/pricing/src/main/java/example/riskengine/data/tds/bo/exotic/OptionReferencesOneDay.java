package example.riskengine.data.tds.bo.exotic;

import example.riskengine.common.bases.time.QLDate;
import example.riskengine.data.tds.bo.repayments.RepaymentCashflows;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OptionReferencesOneDay {
    /**
     * 数据日期
     */
    private QLDate dDataDt;

    /**
     * 单日期权交易要
     */
    private Map<String, OptionReferences> optionReferencesMap = new ConcurrentHashMap<>();


    public QLDate getdDataDt() {
        return dDataDt;
    }

    public void setdDataDt(QLDate dDataDt) {
        this.dDataDt = dDataDt;
    }

    public Map<String, OptionReferences> getOptionReferencesMap() {
        return optionReferencesMap;
    }

    public void setOptionReferencesMap(Map<String, OptionReferences> optionReferencesMap) {
        this.optionReferencesMap = optionReferencesMap;
    }
}
