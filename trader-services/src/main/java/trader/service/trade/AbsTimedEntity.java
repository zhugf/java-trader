package trader.service.trade;

import java.lang.ref.WeakReference;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;

public class AbsTimedEntity  implements TimedEntity, JsonEnabled {

    protected final String id;
    protected final String accountId;
    protected final LocalDate tradingDay;
    protected final Exchangeable instrument;

    public AbsTimedEntity(String id, String accountId, Exchangeable instrument, LocalDate tradingDay) {
        this.id= id;
        this.accountId = accountId;
        this.tradingDay = tradingDay;
        this.instrument = instrument;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getAccountId() {
        return accountId;
    }

    @Override
    public LocalDate getTradingDay() {
        return tradingDay;
    }

    @Override
    public Exchangeable getInstrument() {
        return instrument;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("instrument", instrument.uniqueId());
        json.addProperty("id", id);
        json.addProperty("accountId", accountId);
        json.addProperty("tradingDay", DateUtil.date2str(tradingDay));

        return json;
    }

    private static final Map<String, WeakReference<AbsTimedEntity>> cachedEntities = new HashMap<>();
    private static final ReentrantLock cacheLock = new ReentrantLock();
    public static AbsTimedEntity cacheGet(String entityId) {
        AbsTimedEntity result = null;
        while(!cacheLock.tryLock());
        try {
            WeakReference<AbsTimedEntity> entityRef = cachedEntities.get(entityId);
            if ( entityRef!=null ) {
                if ( (result =entityRef.get())==null ) {
                    cachedEntities.remove(entityId);
                }
            }
        }finally {
            cacheLock.unlock();
        }
        return result;
    }

    public static void cachePut(AbsTimedEntity entity) {
        while(!cacheLock.tryLock());
        try {
            cachedEntities.put(entity.getId(), new WeakReference<AbsTimedEntity>(entity));
        }finally {
            cacheLock.unlock();
        }
    }

}
