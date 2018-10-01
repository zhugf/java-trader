package trader.service.trade;

import java.util.HashMap;
import java.util.Map;

import trader.common.util.ConversionUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;

public class AccountViewImpl implements AccountView {

    private AccountImpl account;
    private String id;
    private long initMargin;
    private Map<String, Integer> maxVolumes;
    private long currMargin;

    public AccountViewImpl(AccountImpl account, Map viewConfig) {
        this.account = account;
        update(viewConfig);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Map<String, Integer> getMaxVolumes() {
        return maxVolumes;
    }

    @Override
    public long getInitMargin() {
        return initMargin;
    }

    @Override
    public long getCurrMargin() {
        return currMargin;
    }

    public void update(Map viewConfig) {
        Map<String, Integer> volumesMap = new HashMap<>();
        String maxVolumes = ConversionUtil.toString(viewConfig.get("maxVolumes"));
        for(String[] kv:StringUtil.splitKVs(maxVolumes)) {
            volumesMap.put(kv[0], ConversionUtil.toInt(kv[1]));
        }
        this.maxVolumes = volumesMap;
        initMargin = PriceUtil.price2long(ConversionUtil.toDouble(viewConfig.get("margin")));
    }

}
