package trader.service.trade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;

public class AccountViewImpl implements AccountView, JsonEnabled {

    private AccountImpl account;
    private String id;
    private long maxMargin;
    private Map<Exchangeable, Integer> exchangeableVolumes = new HashMap<>();
    private Map<String, Integer> maxVolumes;
    private long currMargin;

    private List<PositionImpl> positions =new ArrayList<>();

    public AccountViewImpl(AccountImpl account, Map viewConfig) {
        this.account = account;
        update(viewConfig);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Account getAccount() {
        return account;
    }

    @Override
    public Map<Exchangeable, Integer> getMaxVolumes() {
        return exchangeableVolumes;
    }

    @Override
    public long getMaxMargin() {
        return maxMargin;
    }

    @Override
    public long getCurrMargin() {
        return currMargin;
    }

    @Override
    public List<? extends Position> getPositions(){
        return positions;
    }

    public void resolveExchangeables() {
        TxnFeeEvaluator feeEval = account.getFeeEvaluator();
        if ( null!=feeEval ) {
            var exchangeableVolumes = new HashMap<Exchangeable, Integer>();
            Collection<Exchangeable> allExchangeables = feeEval.getExchangeables();
            for(String key:maxVolumes.keySet()) {
                Integer maxVolume= maxVolumes.get(key);

                for(Exchangeable e:allExchangeables) {
                    if( e.id().startsWith(key) ) {
                        exchangeableVolumes.put(e, maxVolume);
                    }
                }

            }
            this.exchangeableVolumes = exchangeableVolumes;
        }
    }

    /**
     * 判断视图是否归类某个品种
     */
    public boolean accept(Exchangeable e) {
        return exchangeableVolumes.containsKey(e);
    }

    public boolean accept(PositionImpl pos) {
        if (accept(pos.getExchangeable())) {
            if (!positions.contains(pos)) {
                positions.add(pos);
            }
            return true;
        }
        return false;
    }

    public void update(Map viewConfig) {
        Map<String, Integer> volumesMap = new HashMap<>();
        String maxVolumes = ConversionUtil.toString(viewConfig.get("maxVolumes"));
        for(String[] kv:StringUtil.splitKVs(maxVolumes)) {
            volumesMap.put(kv[0], ConversionUtil.toInt(kv[1]));
        }
        this.maxVolumes = volumesMap;
        maxMargin = PriceUtil.price2long(ConversionUtil.toDouble(viewConfig.get("maxMargin")));
        resolveExchangeables();
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("maxMargin", maxMargin);
        return json;
    }

}
