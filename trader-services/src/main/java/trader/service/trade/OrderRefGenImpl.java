package trader.service.trade;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.beans.BeansContainer;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.StringUtil;
import trader.service.repository.BORepository;
import trader.service.repository.BORepositoryConstants.BOEntityType;
import trader.service.trade.TradeConstants.TradeServiceType;

/**
 * <LI>OrderRef ID顺序生成
 * <LI>每交易日唯一
 * <LI>基于KVStore实现序列化和反序列化
 */
public class OrderRefGenImpl implements OrderRefGen, JsonEnabled {
    private AtomicInteger refId = new AtomicInteger();

    private String tradingDay;
    private String entityId=null;
    private BORepository boRepository = null;

    public OrderRefGenImpl(TradeService tradeService, LocalDate tradingDay, BeansContainer beansContainer)
    {
        this.tradingDay = DateUtil.date2str(tradingDay);
        boRepository = beansContainer.getBean(BORepository.class);
        if ( null!=boRepository && tradeService.getType()==TradeServiceType.RealTime ) {
            entityId = DateUtil.date2str(tradingDay)+":OdrRef";
        }
        if ( null!=entityId ) {
            String savedRefJson = boRepository.load(BOEntityType.Default, entityId);
            if( !StringUtil.isEmpty(savedRefJson)) {
                JsonObject json = (JsonObject)JsonParser.parseString(savedRefJson);
                if ( json.has("refId") ) {
                    refId.set(ConversionUtil.toInt(json.get("refId"), true));
                }
            }
        }
    }

    @Override
    public String nextRefId(String accountId) {
        int ref0 = refId.incrementAndGet();
        //多线程方式设置 000xxx 格式的OrderRef
        StringBuilder builder = new StringBuilder();
        String ref0Str = Integer.toString(ref0);
        int prefix0 = 6-ref0Str.length();
        switch(prefix0) {
        case 5:
            builder.append("00000");
            break;
        case 4:
            builder.append("0000");
            break;
        case 3:
            builder.append("000");
            break;
        case 2:
            builder.append("00");
            break;
        case 1:
            builder.append("0");
            break;
        }
        if ( null!=entityId ) {
            boRepository.asynSave(BOEntityType.Default, entityId, this);
        }
        return builder.append(ref0Str).toString();
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", entityId);
        json.addProperty("tradingDay", tradingDay);
        json.addProperty("refId", refId.get());
        return json;
    }

}
