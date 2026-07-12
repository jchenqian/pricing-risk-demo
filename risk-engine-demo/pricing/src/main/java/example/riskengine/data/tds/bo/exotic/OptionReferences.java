package example.riskengine.data.tds.bo.exotic;

public abstract class OptionReferences {
    /**
     * 交易编号
     */
    private String tradeNo;

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }
}
