package trader.service.md;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableType;
import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;

public abstract class AbsMarketDataProducer implements AutoCloseable, MarketDataProducer {
    private final static Logger logger = LoggerFactory.getLogger(AbsMarketDataProducer.class);

    protected MarketDataServiceImpl service;
    protected String id;
    protected volatile Status status;
    protected volatile long statusTime;
    protected Properties connectionProps;

    AbsMarketDataProducer(MarketDataServiceImpl service, Map map){
        this.service = service;
        id = ConversionUtil.toString(map.get("id"));
        status = Status.Initialized;
        connectionProps = StringUtil.text2properties((String)map.get("text"));
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("type", getType().name());
        json.addProperty("status", status.name());
        JsonObject json2 = new JsonObject();
        for(Object k:connectionProps.keySet()) {
            json2.addProperty(k.toString(), connectionProps.getProperty(k.toString()));
        }
        json.add("connectionProps", json2);
        return json;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Properties getConnectionProps() {
        return connectionProps;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public long getStatusTime() {
        return statusTime;
    }

    @Override
	public boolean accept(Exchangeable e) {
    	if ( e.getType()==ExchangeableType.FUTURE ) {
    	    Exchange exchange = e.exchange();
    	    if ( exchange==Exchange.SHFE || exchange==Exchange.CZCE || exchange==Exchange.DCE || exchange==Exchange.CFFEX ) {
    	        return true;
    	    }
    	}
        return false;
    }

    /**
     * 检查配置是否发生变化
     */
    public boolean configEquals(Map map) {
        Properties connectionProps2 = StringUtil.text2properties((String)map.get("text"));
        return connectionProps.equals(connectionProps2);
    }

    @Override
    public void close() {
        service = null;
        close0();
    }

    protected abstract void close0();

    public abstract void asyncConnect();

    public abstract void subscribe(List<Exchangeable> exchangeables);

    protected void changeStatus(Status newStatus) {
        if ( status!=newStatus ) {
            Status lastStatus = status;
            this.status = newStatus;
            logger.info(getId()+" status changed to "+status+", last status: "+lastStatus);
            statusTime = System.currentTimeMillis();
            if ( null!=service ) {
                service.onProducerStatusChanged(this, lastStatus);
            }
        }
    }
}
