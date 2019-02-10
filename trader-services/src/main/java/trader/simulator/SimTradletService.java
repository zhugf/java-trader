package trader.simulator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceErrorConstants;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginService;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.TAService;
import trader.service.trade.MarketTimeService;
import trader.service.trade.TradeService;
import trader.service.tradlet.TradletEvent;
import trader.service.tradlet.TradletGroup;
import trader.service.tradlet.TradletGroupImpl;
import trader.service.tradlet.TradletGroupTemplate;
import trader.service.tradlet.TradletInfo;
import trader.service.tradlet.TradletService;
import trader.service.tradlet.TradletServiceImpl;

/**
 * 模拟交易策略管理服务
 */
public class SimTradletService implements TradletService, ServiceErrorConstants {
    private static final Logger logger = LoggerFactory.getLogger(SimTradletService.class);

    static final String ITEM_TRADLETGROUP = "/TradletService/tradletGroup";
    static final String ITEM_TRADLETGROUPS = ITEM_TRADLETGROUP+"[]";
    static final String ITEM_PLAYBOOK_TEMPLATES = "/TradletService/playbookTemplate[]";
    private BeansContainer beansContainer;
    private MarketTimeService mtService;
    private MarketDataService mdService;
    private TradeService tradeService;
    private PluginService pluginService;
    private TAService taService;

    private Map<String, TradletInfo> tradletInfos = new HashMap<>();
    private List<SimTradletGroupEngine> groupEngines = new ArrayList<>();
    private Map<String, Properties> playbookTemplates = new HashMap<>();

    @Override
    public void init(BeansContainer beansContainer) throws Exception
    {
        this.beansContainer = beansContainer;
        tradeService = beansContainer.getBean(TradeService.class);
        mtService = beansContainer.getBean(MarketTimeService.class);
        mdService = beansContainer.getBean(MarketDataService.class);
        pluginService = beansContainer.getBean(PluginService.class);
        taService = beansContainer.getBean(TAService.class);
        //加载Tradlet
        tradletInfos = TradletServiceImpl.loadStandardTradlets();
        List<Plugin> tradletPlugins = Collections.emptyList();
        if ( pluginService!=null ) {
            tradletPlugins = TradletServiceImpl.filterTradletPlugins(pluginService.getAllPlugins());
        }
        tradletInfos = TradletServiceImpl.reloadTradletInfos(tradletInfos, tradletPlugins, new TreeSet<>());
        //加载TradletGroup
        playbookTemplates = loadPlaybookTemplates();
        groupEngines = loadGroups();
        mdService.addListener((MarketData md)->{
            queueGroupMDEvent(md);
        });
        taService.addListener((Exchangeable e, LeveledTimeSeries series)->{
            queueBarEvent(e, series);
        });
        ScheduledExecutorService scheduledExecutorService = beansContainer.getBean(ScheduledExecutorService.class);
        scheduledExecutorService.scheduleAtFixedRate(()->{
            queueNoopSecondEvent();
        }, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
    }

    @Override
    public Collection<TradletInfo> getTradletInfos() {
        return tradletInfos.values();
    }

    @Override
    public TradletInfo getTradletInfo(String tradletId) {
        return tradletInfos.get(tradletId);
    }

    @Override
    public Collection<TradletGroup> getGroups() {
        List<TradletGroup> result = new ArrayList<>(groupEngines.size());
        for(int i=0;i<groupEngines.size();i++) {
            result.add(groupEngines.get(i).getGroup());
        }
        return result;
    }

    @Override
    public TradletGroup getGroup(String groupId) {
        for(int i=0;i<groupEngines.size();i++) {
            if ( groupEngines.get(i).getGroup().getId().equals(groupId)) {
                return groupEngines.get(i).getGroup();
            }
        }
        return null;
    }

    @Override
    public Map<String, Properties> getPlaybookTemplates() {
        return playbookTemplates;
    }

    /**
     * 模拟行情不支持重新加载
     */
    @Override
    public JsonObject reloadGroups() throws AppException
    {
        JsonObject json = new JsonObject();
        return json;
    }

    /**
     * 派发行情事件到交易组
     */
    private void queueGroupMDEvent(MarketData md) {
        for(int i=0;i<groupEngines.size();i++) {
            SimTradletGroupEngine groupEngine = groupEngines.get(i);
            if ( groupEngine.getGroup().getExchangeable().equals(md.instrumentId) ) {
                groupEngine.queueEvent(TradletEvent.EVENT_TYPE_MD_TICK, md);
            }
        }
    }

    /**
     * 派发KBar事件到交易组
     */
    private void queueBarEvent(Exchangeable e, LeveledTimeSeries series) {
        for(int i=0;i<groupEngines.size();i++) {
            SimTradletGroupEngine groupEngine = groupEngines.get(i);
            if ( groupEngine.getGroup().getExchangeable().equals(e) ) {
                groupEngine.queueEvent(TradletEvent.EVENT_TYPE_MD_BAR, series);
            }
        }
    }

    /**
     * 为空闲的TradletGroup派发NoopSecond事件
     */
    private void queueNoopSecondEvent() {
        long curr = System.currentTimeMillis();
        for(int i=0;i<groupEngines.size();i++) {
            SimTradletGroupEngine groupEngine = groupEngines.get(i);
            if ( (curr-groupEngine.getLastEventTime()) >= TradletEvent.NOOP_TIMEOUT ) {
                groupEngine.queueEvent(TradletEvent.EVENT_TYPE_MISC_NOOP, null);
            }
        }
    }

    private Map<String, Properties> loadPlaybookTemplates() {
        Map<String, Properties> result = new LinkedHashMap<>();
        for(Map templateElem:(List<Map>)ConfigUtil.getObject(ITEM_PLAYBOOK_TEMPLATES)) {
            String templateId = ConversionUtil.toString(templateElem.get("id"));
            String templateConfig = ConversionUtil.toString( templateElem.get("text") );
            Properties templateProps = StringUtil.text2properties(templateConfig);
            result.put(templateId, templateProps);
        }
        return result;
    }

    private List<SimTradletGroupEngine> loadGroups()  throws AppException
    {
        List<SimTradletGroupEngine> result = new ArrayList<>();
        for(Map groupElem:(List<Map>)ConfigUtil.getObject(ITEM_TRADLETGROUPS)) {
            TradletGroupImpl group = createGroup(groupElem);
            SimTradletGroupEngine engine = new SimTradletGroupEngine(group);
            engine.init(beansContainer);
            result.add(engine);
        }
        return result;
    }

    private TradletGroupImpl createGroup(Map groupElem) throws AppException
    {
        String groupId = ConversionUtil.toString(groupElem.get("id"));
        String groupConfig = ConversionUtil.toString( groupElem.get("text") );
        TradletGroupImpl group = new TradletGroupImpl(this, beansContainer, groupId);
        group.update(TradletGroupTemplate.parse(beansContainer, group, groupConfig));
        return group;
    }

}
