package example.riskengine.data.tds.bo.exotic;

import example.riskengine.common.enums.tds.OptionTermType;
import example.riskengine.data.tds.bo.repayments.RepaymentCashflows;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OptionReferencesWali {
    private List<OptionTerms.OptionTermElement> optionTermList;
    private List<OptionPayoffs.OptionPayoffElement> optionPayoffList;
    private List<OptionUnderlyings.OptionUnderlyingElement> optionUnderlyingList;
    private RepaymentCashflows observationDates;
    private Map<OptionTermType, OneForAll.OptionObsByTerm> obsByTerm = new ConcurrentHashMap<>();
    private Map<OptionTermType, List<OptionPayoffs.OptionPayoffElement>> payoffsByTerm = new ConcurrentHashMap<>();

    public OptionReferencesWali(OptionTerms sampleTerms, OptionPayoffs samplePayoffs, OptionUnderlyings sampleUnderlyings, RepaymentCashflows observationDates) {
        initOptionReferences();
    }


    public void initOptionReferences() {
        OptionTerms sampleTerms = new OptionTerms();
        OptionPayoffs samplePayoffs = new OptionPayoffs();
        OptionUnderlyings sampleUnderlyings = new OptionUnderlyings();
        this.optionTermList = sampleTerms.getOptionTermList();
        this.optionPayoffList = samplePayoffs.getOptionPayoffList();
        this.optionUnderlyingList = sampleUnderlyings.getOptionUnderlyingElementList();

        //add

    }





}
