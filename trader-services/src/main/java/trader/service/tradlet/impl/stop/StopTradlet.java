package trader.service.tradlet.impl.stop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.util.JsonEnabled;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.ta.LeveledBarSeries;
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
 * 简单止盈止损策略, 用于开仓后一段时间内止损, 需要Playbook属性中明确止损幅度.
 *
 * <BR>STOP Tradlet不需要参数设置, 它从Playbook 属性读取停止参数, 这些参数可以从 playbookTemplates 配置合并
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "STOP")
public class StopTradlet implements Tradlet, TradletConstants {
    private final static Logger logger = LoggerFactory.getLogger(StopTradlet.class);

    public static class PriceTrend implements JsonEnabled{

        @Override
        public JsonElement toJson() {
            JsonObject json = new JsonObject();

            return json;
        }
    }

    private static class StopTradletRuntime{
        int version;
        AbsStopPolicy[] policies;
    }

    private BeansContainer beansContainer;
    private MarketDataService mdService;
    private MarketTimeService mtService;
    private TradletGroup group;
    private PlaybookKeeper playbookKeeper;

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
        }
    }

    @Override
    public void destroy() {

    }

    public String queryData(String queryExpr) {
        return null;
    }

    @Override
    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple) {
        if ( oldStateTuple==null ) {
            //从Playbook 属性构建运行时数据.
            buildRuntime(playbook, null);
        }
    }

    @Override
    public void onTick(MarketData marketData) {
        checkActivePlaybooks(marketData);
    }

    @Override
    public void onNewBar(LeveledBarSeries series) {
    }

    @Override
    public void onNoopSecond() {
        checkActivePlaybooks(null);
    }

    private void checkActivePlaybooks(MarketData tick) {
        if ( tick==null ) {
            return;
        }
        for(Playbook playbook:playbookKeeper.getActivePlaybooks(null)) {
            if ( !playbook.getInstrument().equals(tick.instrument)) {
                continue;
            }
            String closeReason = needStop(playbook, tick);
            if ( closeReason!=null ) {
                logger.info("Playbook "+playbook.getId()+" stop "+closeReason);
                PlaybookCloseReq closeReq = new PlaybookCloseReq();
                closeReq.setActionId(closeReason);
                playbookKeeper.closePlaybook(playbook, closeReq);
            }
        }
    }

    /**
     * 检查是否需要立刻止损
     */
    private String needStop(Playbook playbook, MarketData tick) {
        String result = null;
        StopTradletRuntime runtime = rebuildRuntime(playbook);
        AbsStopPolicy[] policies = null;
        if ( runtime!=null ) {
            policies = runtime.policies;
        }
        if ( policies!=null ) {
            for(int i=0;i<policies.length;i++) {
                if ( policies[i]==null ) {
                    continue;
                }
                result = policies[i].needStop(playbook, tick);
                if ( result!=null) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 如果版本发生变化, 重新构建StopTradlet Runtime
     */
    private StopTradletRuntime rebuildRuntime(Playbook playbook) {
        StopTradletRuntime runtime = (StopTradletRuntime)playbook.getAttr(PBATTR_STOP_RUNTIME);
        if ( runtime==null ) {
            runtime = buildRuntime(playbook, runtime);
        } else if ( runtime.version!=playbook.getAttrVersion() ) {
            runtime = buildRuntime(playbook, runtime);
        }
        return runtime;
    }

    private StopTradletRuntime buildRuntime(Playbook playbook, StopTradletRuntime runtime)
    {
        if ( runtime==null ) {
            runtime = new StopTradletRuntime();
            runtime.policies = new AbsStopPolicy[StopPolicy.values().length];
        }
        AbsStopPolicy[] policies = runtime.policies;

        boolean runtimeBuilt = false;
        int idx = StopPolicy.SimplePriceAbove.ordinal();
        if ( (policies[idx]==null && SimplePriceAbovePolicy.needPolicy(playbook)) || policies[idx]!=null && policies[idx].needRebuild(playbook)) {
            policies[idx] = new SimplePriceAbovePolicy(beansContainer, playbook);
            runtimeBuilt = true;
        }
        idx = StopPolicy.SimplePriceBelow.ordinal();
        if ( (policies[idx]==null && SimplePriceBelowPolicy.needPolicy(playbook)) || policies[idx]!=null && policies[idx].needRebuild(playbook)) {
            policies[idx] = new SimplePriceBelowPolicy(beansContainer, playbook);
            runtimeBuilt = true;
        }
        idx = StopPolicy.MaxLifeTime.ordinal();
        if ( (policies[idx]==null && MaxLifeTimePolicy.needPolicy(playbook)) || policies[idx]!=null && policies[idx].needRebuild(playbook)) {
            policies[idx] = new MaxLifeTimePolicy(beansContainer, playbook);
            runtimeBuilt = true;
        }
        idx = StopPolicy.EndTime.ordinal();
        if ( (policies[idx]==null && EndTimePolicy.needPolicy(playbook)) || policies[idx]!=null && policies[idx].needRebuild(playbook)) {
            policies[idx] = new EndTimePolicy(beansContainer, playbook);
            runtimeBuilt = true;
        }
        idx = StopPolicy.TripPriceAbove.ordinal();
        if ( (policies[idx]==null && TripPriceAbovePolicy.needPolicy(playbook)) || policies[idx]!=null && policies[idx].needRebuild(playbook)) {
            policies[idx] = new TripPriceAbovePolicy(beansContainer, playbook);
            runtimeBuilt = true;
        }
        idx = StopPolicy.TripPriceBelow.ordinal();
        if ( (policies[idx]==null && TripPriceBelowPolicy.needPolicy(playbook)) || policies[idx]!=null && policies[idx].needRebuild(playbook)) {
            policies[idx] = new TripPriceBelowPolicy(beansContainer, playbook);
            runtimeBuilt = true;
        }
        idx = StopPolicy.BarrieredPriceUp.ordinal();
        if ( (policies[idx]==null && BarrieredPriceUpPolicy.needPolicy(playbook)) || policies[idx]!=null && policies[idx].needRebuild(playbook)) {
            policies[idx] = new BarrieredPriceUpPolicy(beansContainer, playbook);
            runtimeBuilt = true;
        }
        idx = StopPolicy.BarrieredPriceDown.ordinal();
        if ( (policies[idx]==null && BarrieredPriceDownPolicy.needPolicy(playbook)) || policies[idx]!=null && policies[idx].needRebuild(playbook)) {
            policies[idx] = new BarrieredPriceDownPolicy(beansContainer, playbook);
            runtimeBuilt = true;
        }

        playbook.setAttr(PBATTR_STOP_RUNTIME, runtime);
        runtime.version = playbook.getAttrVersion();

        if ( runtimeBuilt ) {
            logger.info("Playbook "+playbook.getId()+" stop runtime is built");
        }
        return runtime;
    }

}
