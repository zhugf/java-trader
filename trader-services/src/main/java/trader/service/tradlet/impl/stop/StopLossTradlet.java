package trader.service.tradlet.impl.stop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.util.JsonEnabled;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.ta.LeveledTimeSeries;
import trader.service.trade.MarketTimeService;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.PlaybookCloseReq;
import trader.service.tradlet.PlaybookKeeper;
import trader.service.tradlet.PlaybookStateTuple;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletConstants;
import trader.service.tradlet.TradletContext;
import trader.service.tradlet.TradletGroup;

/**
 * 简单止损策略, 用于开仓后一段时间内止损, 需要Playbook属性中明确止损幅度.
 * <BR>目前使用止损方式
 * <LI>价格阶梯止损: 在某个价格之上保持一段时间即止损.
 * <LI>最长持仓时间: 到达最大持仓时间后, 即平仓
 * <LI>最后持仓时间: 到达某绝对市场时间, 即平仓
 *
 * 需要为每个playbook实例构建运行时数据, 保证tradlet重新加载后可用.
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "StopLoss")
public class StopLossTradlet implements Tradlet, TradletConstants {
    private final static Logger logger = LoggerFactory.getLogger(StopLossTradlet.class);

    public static class PriceTrend implements JsonEnabled{

        @Override
        public JsonElement toJson() {
            JsonObject json = new JsonObject();

            return json;
        }
    }

    public static class EndTime implements JsonEnabled{

        @Override
        public JsonElement toJson() {
            JsonObject json = new JsonObject();
            return json;
        }
    }

    private BeansContainer beansContainer;
    private MarketDataService mdService;
    private MarketTimeService mtService;
    private TradletGroup group;
    private PlaybookKeeper playbookKeeper;
    private JsonObject templates;

    @Override
    public void init(TradletContext context) throws Exception
    {
        beansContainer = context.getBeansContainer();
        group = context.getGroup();
        playbookKeeper = context.getGroup().getPlaybookKeeper();
        mdService = beansContainer.getBean(MarketDataService.class);
        mtService = beansContainer.getBean(MarketTimeService.class);
        reload(context);
    }

    @Override
    public void reload(TradletContext context) throws Exception {
        if ( !StringUtil.isEmpty(context.getConfigText())) {
            templates = (JsonObject)(new JsonParser()).parse(context.getConfigText());
        }
    }

    @Override
    public void destroy() {

    }

    @Override
    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple) {
        if ( oldStateTuple==null ) {
            //从Playbook 属性构建运行时数据.
            playbook.setAttr(PBATTR_STOPLOSS_RUNTIME, buildRuntime(playbook));
        }
    }

    @Override
    public void onTick(MarketData marketData) {
        checkActivePlaybooks(marketData);
    }

    @Override
    public void onNewBar(LeveledTimeSeries series) {
    }

    @Override
    public void onNoopSecond() {
        checkActivePlaybooks(null);
    }

    private void checkActivePlaybooks(MarketData md) {
        for(Playbook playbook:playbookKeeper.getActivePlaybooks(null)) {
            long newPrice = 0;
            if ( md!=null && playbook.getExchangable().equals(md.instrumentId) ) {
                newPrice = md.lastPrice;
            }
            String closeReason = needStopLoss(playbook, newPrice);
            if ( closeReason!=null ) {
                PlaybookCloseReq closeReq = new PlaybookCloseReq();
                closeReq.setActionId(closeReason);
                playbookKeeper.closePlaybook(playbook, closeReq);
            }
        }
    }

    /**
     * 检查是否需要立刻止损
     */
    private String needStopLoss(Playbook playbook, long newPrice) {
        AbsStopPolicy[] runtime = (AbsStopPolicy[])playbook.getAttr(PBATTR_STOPLOSS_RUNTIME);
        if ( runtime==null ) {
            return null;
        }
        for(int i=0;i<runtime.length;i++) {
            if ( runtime[i]!=null ) {
                String closeAction = runtime[i].needStop(playbook, newPrice);
                if ( closeAction!=null) {
                    return closeAction;
                }
            }
        }
        return null;
    }

    private AbsStopPolicy[] buildRuntime(Playbook playbook)
    {
        AbsStopPolicy[] result = null;
        JsonObject template = (JsonObject)templates.get(playbook.getTemplateId());
        if (template==null) {
            template = (JsonObject)templates.get("default");
        }
        if ( template!=null ) {
            long openingPrice = playbook.getMoney(PBMny_Opening);
            if ( openingPrice==0 ) {
                openingPrice = mdService.getLastData(playbook.getExchangable()).lastPrice;
            }
            result = new AbsStopPolicy[StopLossPolicy.values().length];
            //PriceStep
            if ( template.has("priceSteps") ) {
                result[StopLossPolicy.PriceStep.ordinal()] = new PriceStepPolicy(beansContainer, playbook, openingPrice, template.get("priceSteps"));
            }
            if ( template.has("priceTrend")) {
                result[StopLossPolicy.PriceTrend.ordinal()] = new PriceTrendPolicy(beansContainer, playbook, openingPrice, template.get("priceTrend"));
            }
            //MaxLifeTime
            if ( template.has("maxLifeTime")) {
                result[StopLossPolicy.MaxLifeTime.ordinal()] = new MaxLifeTimePolicy(beansContainer, template.get("maxLifeTime"));
            }
            //EndTime
            if ( template.has("endTime")) {
                result[StopLossPolicy.EndTime.ordinal()] = new EndTimePolicy(beansContainer, playbook, template.get("endTime"));
            }
        }
        return result;
    }

}
