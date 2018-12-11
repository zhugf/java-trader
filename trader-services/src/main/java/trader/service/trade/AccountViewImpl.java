package trader.service.trade;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
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

    private Map<Exchangeable, PositionImpl> positions =new HashMap<>();

    public AccountViewImpl(AccountImpl account, Map viewConfig) {
        id = ConversionUtil.toString(viewConfig.get("id"));
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

    /**
     * TODO 尚未实现
     */
    @Override
    public long getCurrMargin() {
        return 0;
    }

    @Override
    public Collection<Position> getPositions(){
        return (Collection)positions.values();
    }

    @Override
    public Position getPosition(Exchangeable e) {
        return positions.get(e);
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

    /**
     * 账户视图是否接受指定Position
     */
    public boolean accept(PositionImpl pos) {
        if (accept(pos.getExchangeable())) {
            positions.put(pos.getExchangeable(), pos);
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
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("maxMargin", PriceUtil.long2str(maxMargin));
        return json;
    }

}
