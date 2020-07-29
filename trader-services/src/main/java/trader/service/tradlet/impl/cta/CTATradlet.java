package trader.service.tradlet.impl.cta;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.FileUtil;
import trader.common.util.FileWatchListener;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.repository.BORepository;
import trader.service.repository.BORepositoryConstants.BOEntityType;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.TechnicalAnalysisAccess;
import trader.service.ta.TechnicalAnalysisService;
import trader.service.trade.MarketTimeService;
import trader.service.trade.TradeConstants.OrderPriceType;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.trade.TradeConstants.TradeServiceType;
import trader.service.trade.TradeService;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.PlaybookBuilder;
import trader.service.tradlet.PlaybookCloseReq;
import trader.service.tradlet.PlaybookKeeper;
import trader.service.tradlet.PlaybookStateTuple;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletConstants.PlaybookState;
import trader.service.tradlet.TradletContext;
import trader.service.tradlet.TradletGroup;
import trader.service.tradlet.impl.cta.CTAConstants.CTARuleState;
/**
 * CTA辅助策略交易小程序，由 $TRADER_HOME/etc/cta-hints.xml 配置文件定义的策略驱动执行，基于xml/json语法定义
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "CTA")
public class CTATradlet implements Tradlet, FileWatchListener, JsonEnabled {
    private final static Logger logger = LoggerFactory.getLogger(CTATradlet.class);

    private final static String ATTR_CTA_RULE_ID = "ctaRuleId";

    private TradeService tradeService;
    private BeansContainer beansContainer;
    private BORepository repository;
    private File hintFile;
    private MarketTimeService mtService;
    private TradletGroup group;
    private TechnicalAnalysisService taService;
    private PlaybookKeeper playbookKeeper;
    /**
     * 用于加载/保存配置的唯一ID
     */
    private String entityId;
    /**
     * 全部(含历史)CTAHint
     */
    private List<CTAHint> hints = new ArrayList<>();
    /**
     * 全部(含历史)CTA规则记录
     */
    private Map<String, CTARuleLog> ruleLogs = new LinkedHashMap<>();
    /**
     * 当前可进场的CTA规则记录
     */
    private Map<Exchangeable, List<CTARule>> toEnterRulesByInstrument = new LinkedHashMap<>();
    /**
     * 当前活动CTA规则记录: RuleState=toEnter,Holding
     */
    private Map<String, CTARule> activeRulesById = new LinkedHashMap<>();

    @Override
    public void init(TradletContext context) throws Exception
    {
        beansContainer = context.getBeansContainer();
        group = context.getGroup();
        entityId = group.getId()+":CTA";
        repository = beansContainer.getBean(BORepository.class);
        playbookKeeper = group.getPlaybookKeeper();
        mtService = beansContainer.getBean(MarketTimeService.class);
        taService = beansContainer.getBean(TechnicalAnalysisService.class);
        //实际环境下, 监控hints文件
        initHintFile(context);
        loadState();
        reloadHints(context);
    }

    @Override
    public void reload(TradletContext context) throws Exception
    {

    }

    @Override
    public void destroy() {
    }

    @Override
    public Object onRequest(String path, Map<String, String> params, String payload) {
        if (StringUtil.equalsIgnoreCase("cta/hints", path) ) {
            return JsonUtil.object2json(hints);
        } else if (StringUtil.equalsIgnoreCase("cta/ruleLogs", path) ) {
            return JsonUtil.object2json(ruleLogs.values());
        } else if (StringUtil.equalsIgnoreCase("cta/activeRules", path) ) {
            return JsonUtil.object2json(activeRulesById.values());
        } else if (StringUtil.equalsIgnoreCase("cta/activeRuleIds", path) ) {
            return JsonUtil.object2json(activeRulesById.keySet());
        } else if (StringUtil.equalsIgnoreCase("cta/toEnterInstruments", path) ) {
            return JsonUtil.object2json(toEnterRulesByInstrument.keySet());
        }
        return null;
    }

    @Override
    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple)
    {

    }

    @Override
    public void onTick(MarketData tick) {
        boolean changed = tryClosePlaybooks(tick);
        changed |= ruleMatchForOpen(tick);
        if ( changed ) {
            asyncSaveState();
        }
    }

    @Override
    public void onNewBar(LeveledBarSeries series) {
    }

    @Override
    public void onNoopSecond() {
    }

    /**
     * 匹配CTA规则
     */
    private boolean ruleMatchForOpen(MarketData tick) {
        boolean result = false;
        List<CTARule> rules = toEnterRulesByInstrument.get(tick.instrument);
        if ( null!=rules ) {
            TechnicalAnalysisAccess taAccess = taService.forInstrument(tick.instrument);
            for(int i=0;i<rules.size();i++) {
                CTARule rule0 = rules.get(i);
                if ( rule0.disabled ) {
                    continue;
                }
                CTARuleLog ruleLog = ruleLogs.get(rule0.id);
                if ( ruleLog!=null && ruleLog.state==CTARuleState.ToEnter ) {
                    //是否需要discard
                    if ( rule0.matchDiscard(tick) ) {
                        rules.remove(rule0);
                        ruleLog.changeState(CTARuleState.Discarded, tick.updateTime+" 未进场撤@"+PriceUtil.long2str(tick.lastPrice));
                        result = true;
                        continue;
                    }
                    if ( rule0.matchEnter(tick, taAccess) ) {
                        createPlaybookFromRule(rule0, tick);
                        rules.remove(rule0);
                        result = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * 创建一个Playbook, 应用CTA 策略
     */
    private void createPlaybookFromRule(CTARule rule, MarketData tick) {
        PlaybookBuilder builder = new PlaybookBuilder();
        long price = tick.lastPrice;
        if ( rule.dir==PosDirection.Long ) {
            price = tick.lastAskPrice();
        } else {
            price = tick.lastBidPrice();
        }
        builder.setInstrument(tick.instrument)
            .setOpenDirection(rule.dir)
            .setVolume(rule.volume)
            .setAttr(ATTR_CTA_RULE_ID, rule.id)
            .setPriceType(OrderPriceType.LimitPrice)
            .setOpenPrice(price)
            ;
        try{
            Playbook playbook = playbookKeeper.createPlaybook(this, builder);
            CTARuleLog ruleLog = ruleLogs.get(rule.id);
            if ( ruleLog!=null ) {
                ruleLog.changeState(CTARuleState.Holding, tick.updateTime+" 进场@"+PriceUtil.long2str(price));
            }
            playbook.open();
            logger.info("Tradlet group "+group.getId()+" 合约 "+tick.instrument+" CTA 策略 "+rule.id+" 进场: "+playbook.getId());
        }catch(Throwable t) {
            logger.error("Tradlet group "+group.getId()+" 合约 "+tick.instrument+" CTA 策略 "+rule.id+" 创建交易剧本失败: ", t);
        }
    }

    /**
     * 尝试关闭Playbook
     */
    private boolean tryClosePlaybooks(MarketData tick) {
        boolean result = false;
        List<Playbook> playbooks = playbookKeeper.getActivePlaybooks(null);
        if ( null!=playbooks && !playbooks.isEmpty() ) {
            for(Playbook pb:playbooks) {
                //必须是 Opened 状态, 且合约相同
                if ( !pb.getInstrument().equals(tick.instrument) || pb.getStateTuple().getState()!=PlaybookState.Opened ) {
                    continue;
                }
                PlaybookCloseReq closeReq = null;
                String ctaRuleId = (String)pb.getAttr(ATTR_CTA_RULE_ID);
                if ( StringUtil.isEmpty(ctaRuleId) ) {
                    continue;
                }
                CTARule rule = activeRulesById.get(ctaRuleId);
                if ( null!=rule ) {
                    CTARuleLog ruleLog = ruleLogs.get(rule.id);
                    if ( rule.matchStop(tick) ) {
                        closeReq = new PlaybookCloseReq();
                        String actionId = "stopLoss@"+PriceUtil.long2str(tick.lastPrice);
                        closeReq.setActionId(actionId);
                        if ( ruleLog!=null ) {
                            ruleLog.changeState(CTARuleState.StopLoss, tick.updateTime+" 止损@"+PriceUtil.long2str(tick.lastPrice));
                        }
                    }
                    if ( rule.matchTake(tick)) {
                        closeReq = new PlaybookCloseReq();
                        closeReq.setActionId("takeProfit@"+PriceUtil.long2str(tick.lastPrice));
                        if ( ruleLog!=null ) {
                            ruleLog.changeState(CTARuleState.TakeProfit, tick.updateTime+" 止盈@"+PriceUtil.long2str(tick.lastPrice));
                        }
                    }
                    if ( rule.matchEnd(tick)) {
                        closeReq = new PlaybookCloseReq();
                        closeReq.setActionId("timeout@"+PriceUtil.long2str(tick.lastPrice));
                        if ( ruleLog!=null ) {
                            ruleLog.changeState(CTARuleState.Timeout, tick.updateTime+" 超时@"+PriceUtil.long2str(tick.lastPrice));
                        }
                    }
                }
                if ( null!=closeReq ) {
                    playbookKeeper.closePlaybook(pb, closeReq);
                    logger.info("Tradlet group "+group.getId()+" 合约 "+tick.instrument+" CTA 策略 "+rule.id+" 平仓: "+pb.getId());
                    result = true;
                }
            }
        }
        return result;
    }

    private void initHintFile(TradletContext context) throws IOException
    {
        Properties props = context.getConfigAsProps();
        String fileName = props.getProperty("file");
        if ( StringUtil.isEmpty(fileName)) {
            hintFile = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_ETC), "cta-hints.xml");
        } else {
            hintFile = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_ETC), fileName);
        }
        hintFile = hintFile.getAbsoluteFile();
        logger.info("Group "+group.getId()+" 加载 CTA 策略文件: "+hintFile);
        tradeService = beansContainer.getBean(TradeService.class);
        if ( tradeService.getType()==TradeServiceType.RealTime ) {
            FileUtil.watchOn(hintFile, this);
        }
    }

    /**
     * 加载cta-hints.xml文件.
     * <BR>如文件更新, 会重复调用此函数
     * @param context 初始化的上下文
     */
    private void reloadHints(TradletContext context) throws Exception
    {
        LocalDate tradingDay = mtService.getTradingDay();
        //加载全部Hint
        List<CTAHint> hints = CTAHint.loadHints(hintFile, tradingDay);
        Map<String, CTARuleLog> ruleLogs = new LinkedHashMap<>(this.ruleLogs);
        Set<String> newRuleIds = new TreeSet<>();
        Set<Exchangeable> newRuleInstruments = new TreeSet<>();
        Map<Exchangeable, List<CTARule>> toEnterRulesByInstrument = new LinkedHashMap<>();
        List<String> toEnterRuleIds = new ArrayList<>();
        Map<String, CTARule> activeRulesById = new LinkedHashMap<>();
        Set<Exchangeable> activeRuleInstruments = new TreeSet<>();

        for(CTAHint hint:hints) {
            //忽略不可用Hint
            boolean hintValid = hint.isValid(tradingDay);
            for(CTARule rule:hint.rules) {
                boolean ruleValid = hintValid && !rule.disabled;
                CTARuleLog ruleLog = ruleLogs.get(rule.id);
                if ( null==ruleLog && ruleValid) {
                    ruleLog = new CTARuleLog(rule);
                    ruleLogs.put(ruleLog.id, ruleLog);
                    newRuleIds.add(rule.id);
                    newRuleInstruments.add(hint.instrument);
                }
                if ( !ruleValid) {
                    if ( null!=ruleLog&& !ruleLog.state.isDone() ) {
                        ruleLog.changeState(CTARuleState.Discarded, LocalDateTime.now()+" 规则禁用");
                    }
                    continue;
                }
                //true==ruleValid
                if ( CTARuleState.ToEnter==ruleLog.state ) {
                    List<CTARule> toEnterRules = toEnterRulesByInstrument.get(hint.instrument);
                    if ( null==toEnterRules ) {
                        toEnterRules = new ArrayList<>();
                        toEnterRulesByInstrument.put(hint.instrument, toEnterRules);
                    }
                    toEnterRules.add(rule);
                    toEnterRuleIds.add(rule.id);
                }
                if ( !ruleLog.state.isDone() ) {
                    activeRulesById.put(rule.id, rule);
                    activeRuleInstruments.add(hint.instrument);

                    if ( null!=context ) {
                        context.addInstrument(hint.instrument);
                    }
                }
            }
        }
        //覆盖原始的值
        this.hints = hints;
        this.ruleLogs = ruleLogs;
        this.toEnterRulesByInstrument = toEnterRulesByInstrument;
        this.activeRulesById = activeRulesById;

        logger.info("Group "+group.getId()+" 加载CTA策略"
                +", 待入场合约: "+toEnterRulesByInstrument.keySet()
                +", 待入场规则ID: "+toEnterRuleIds
                +", 活跃合约: "+activeRuleInstruments
                +", 活跃规则ID: "+activeRulesById);
    }

    @Override
    public void onFileChanged(File file) {
        try{
            reloadHints(null);
        }catch(Throwable t) {
            logger.error("CTA 策略文件 "+file+" 重新加载失败", t);
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("accountId", group.getAccount().getId());
        json.add("ruleLogs", JsonUtil.object2json(ruleLogs.values()));
        return json;
    }

    /**
     * 从数据库恢复状态
     */
    private void loadState() {
        String jsonStr = repository.load(BOEntityType.Default, entityId);
        if ( !StringUtil.isEmpty(jsonStr) ) {
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
            if ( json.has("ruleLogs")) {
                JsonArray array = json.get("ruleLogs").getAsJsonArray();
                LinkedHashMap<String, CTARuleLog> ruleLogs = new LinkedHashMap<>();
                for(int i=0; i<array.size();i++) {
                    CTARuleLog ruleLog = new CTARuleLog(array.get(i).getAsJsonObject());
                    ruleLogs.put(ruleLog.id, ruleLog);
                }
                this.ruleLogs = ruleLogs;
            }
            logger.info("Group "+group.getId()+" 加载 CTA 规则记录: "+ruleLogs.size());
        }
    }

    /**
     * 异步保存状态
     */
    private void asyncSaveState() {
        repository.asynSave(BOEntityType.Default, entityId, this);
    }

}
