package trader.service.trade;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.util.StringUtil;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.beans.BeansContainer;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.service.repository.BORepository;
import trader.service.repository.BORepositoryConstants.BOEntityType;
import trader.service.trade.TradeConstants.TradeServiceType;

/**
 * <LI>OrderRef ID顺序生成
 * <LI>每交易日唯一
 * <LI>基于KVStore实现序列化和反序列化
 */
public class OrderRefGenImpl implements OrderRefGen {
    private AtomicInteger refId = new AtomicInteger();

    private ExecutorService executorService;
    private String tradingDay;
    private String entityId=null;
    private BORepository boRepository = null;

    public OrderRefGenImpl(TradeService tradeService, LocalDate tradingDay, BeansContainer beansContainer)
    {
        executorService = beansContainer.getBean(ExecutorService.class);
        this.tradingDay = DateUtil.date2str(tradingDay);
        boRepository = beansContainer.getBean(BORepository.class);
        if ( null!=boRepository && tradeService.getType()==TradeServiceType.RealTime ) {
            entityId = DateUtil.date2str(tradingDay)+".OdrRef";
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
            executorService.execute(()->{
                JsonObject json = new JsonObject();
                json.addProperty("id", entityId);
                json.addProperty("tradingDay", tradingDay);
                json.addProperty("refId", refId.get());
                boRepository.asynSave(BOEntityType.Default, entityId, json);
            });
        }
        return builder.append(ref0Str).toString();
    }

}
