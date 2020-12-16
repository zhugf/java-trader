package trader.service.trade;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.Future;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.repository.BORepository;
import trader.service.repository.BORepositoryConstants.BOEntityType;
import trader.service.trade.TradeConstants.OdrVolume;
import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;

/**
 * 记载一条成交的明细
 */
public class TransactionImpl extends AbsTimedEntity implements Transaction, JsonEnabled {
    private String orderId;
    private OrderDirection direction;
    private OrderOffsetFlag offsetFlag;
    private int volume;
    private long price;
    private long time;
    private Object txnData;
    private List<PositionDetail> closedDetails = Collections.emptyList();
    private PositionDetail openDetail;

    public TransactionImpl(String id, String accountId, Exchangeable instrument, LocalDate tradingDay, String orderId, OrderDirection direction, OrderOffsetFlag offsetFlag, long price, int volume, long time, Object txnData) {
		super(id, accountId, instrument, tradingDay);
    	this.orderId = orderId;
		this.direction = direction;
		this.offsetFlag = offsetFlag;
		this.price = price;
		this.volume = volume;
		this.time = time;
		this.txnData = txnData;
	}

	private TransactionImpl(BORepository repository, JsonObject json) {
	    super(JsonUtil.getProperty(json, "id", null),
                JsonUtil.getProperty(json,"accountId",null),
                Future.fromString(json.get("instrument").getAsString()),
                JsonUtil.getPropertyAsDate(json, "tradingDay")
            );
        this.orderId = JsonUtil.getProperty(json,"orderId",null);
        this.direction = JsonUtil.getPropertyAsEnum(json, "direction", OrderDirection.Buy, OrderDirection.class);
        this.offsetFlag = JsonUtil.getPropertyAsEnum(json, "offsetFlag", OrderOffsetFlag.OPEN, OrderOffsetFlag.class);
        this.volume = JsonUtil.getPropertyAsInt(json, "volume", 0);
        this.price = JsonUtil.getPropertyAsPrice(json, "price", 0);
        this.time = JsonUtil.getPropertyAsLong(json, "time", 0);
        this.txnData = JsonUtil.getProperty(json, "txnData", null);
    }

    @Override
    public String getOrderId() {
        return orderId;
    }

    @Override
    public OrderDirection getDirection() {
        return direction;
    }

    @Override
    public OrderOffsetFlag getOffsetFlags() {
        return offsetFlag;
    }

    @Override
    public int getVolume() {
        return volume;
    }

    @Override
    public long getPrice() {
        return price;
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public PositionDetail getOpenDetail() {
        return openDetail;
    }

    @Override
    public List<PositionDetail> getClosedDetails(){
        return closedDetails;
    }

    public void setOpenDetail(PositionDetail openDetail) {
        this.openDetail = openDetail;
    }

    public void setClosedDetails(List<PositionDetail> closedDetails) {
        this.closedDetails = closedDetails;
    }

    public JsonElement toJson() {
        JsonObject json = super.toJson().getAsJsonObject();
        json.addProperty("orderId", orderId);
        json.addProperty("direction", direction.name());
        json.addProperty("offsetFlag", offsetFlag.name());
        json.addProperty("volume", volume);
        json.addProperty("price", PriceUtil.long2str(price));
        json.addProperty("time", time);
        json.addProperty("txnData", ConversionUtil.toString(txnData));
        return json;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(128);
        result
            .append(getId()).append("/").append(orderId)
            .append(" ").append(getOffsetFlags())
            .append(" ").append(getDirection())
            .append(" P").append(PriceUtil.long2str(getPrice()))
            .append(" V").append(getVolume());
        return result.toString();
    }

    public boolean equals(Object o) {
        if ( o==null || !(o instanceof TransactionImpl)) {
            return false;
        }
        TransactionImpl t = (TransactionImpl)o;
        return StringUtil.equals(id, t.id) && StringUtil.equals(accountId, t.accountId);
    }

    public int hashCode() {
        return id.hashCode();
    }

    /**
     * 加载并恢复数据
     */
    public static TransactionImpl load(BORepository repository, String txnId, String data) {
        TransactionImpl result = (TransactionImpl)cacheGet(txnId);
        if ( null==result ){
            String jsonText = data;
            if (null==jsonText) {
                jsonText = repository.load(BOEntityType.Transaction, txnId);
            }
            if ( !StringUtil.isEmpty(jsonText)) {
                JsonObject json = (JsonObject)JsonParser.parseString(jsonText);
                result = new TransactionImpl(repository, json);
                cachePut(result);
            }
        }
        return result;
    }

}
