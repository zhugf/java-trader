package trader.service.tradlet.impl.cta;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.FileUtil;
import trader.common.util.FileWatchListener;
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
/**
 * CTA辅助策略交易小程序，由 $TRADER_HOME/etc/cta-hints.xml 配置文件定义的策略驱动执行，基于xml/json语法定义
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "CTA")
public class CTATradlet implements Tradlet, FileWatchListener {
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
    private Map<Exchangeable, List<CTAHint>> ctaHintsByInstrument = new HashMap<>();
    private Map<String, CTABreakRule> ctaRulesById = new HashMap<>();
    /**
     * 已经处理的CTA 策略ID列表
     */
    private Set<String> usedPolicyIds = new TreeSet<>();


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
        Properties props = context.getConfigAsProps();
        String fileName = props.getProperty("file");
        if ( StringUtil.isEmpty(fileName)) {
            hintFile = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_ETC), "cta-hints.xml");
        } else {
            hintFile = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_ETC), fileName);
        }
        hintFile = hintFile.getAbsoluteFile();
        logger.info("Group "+group.getId()+" 加载 CTA 策略文件: "+hintFile);
        reloadHints(context);
        //实际环境下, 监控hints文件
        tradeService = beansContainer.getBean(TradeService.class);
        if ( tradeService.getType()==TradeServiceType.RealTime ) {
            FileUtil.watchOn(hintFile, this);
        }
        restoreState();
    }

    @Override
    public void reload(TradletContext context) throws Exception
    {

    }

    @Override
    public void destroy() {
    }

    @Override
    public String onRequest(String path, String payload, Map<String, String> params) {
        if (StringUtil.equalsIgnoreCase("cta/hints", path) ) {
            return JsonUtil.object2json(ctaHintsByInstrument.values()).toString();
        }
        return null;
    }

    @Override
    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple)
    {

    }

    @Override
    public void onTick(MarketData tick) {
        tryClosePlaybooks(tick);
        CTABreakRule rule = ruleMatchForOpen(tick);
        if ( null!=rule ) {
            //创建playbook
            createPlaybookFromRule(rule, tick);
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
    private CTABreakRule ruleMatchForOpen(MarketData tick) {
        CTABreakRule rule = null;
        List<CTAHint> hints = ctaHintsByInstrument.get(tick.instrument);
        if ( null!=hints ) {
            TechnicalAnalysisAccess taAccess = taService.forInstrument(tick.instrument);
            for(int i=0;i<hints.size();i++) {
                CTAHint hint = hints.get(i);
                for(int j=0;j<hint.rules.length;j++) {
                    CTABreakRule policy0 = hint.rules[j];
                    if ( !usedPolicyIds.contains(policy0.id) && policy0.matchOpen(tick, taAccess) ) {
                        rule = policy0;
                        break;
                    }
                }
                if ( null!=rule ) {
                    break;
                }
            }
        }
        return rule;
    }

    /**
     * 创建一个Playbook, 应用CTA 策略
     */
    private void createPlaybookFromRule(CTABreakRule rule, MarketData tick) {
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
            usedPolicyIds.add(rule.id);
            playbook.open();
            logger.info("Tradlet group "+group.getId()+" 合约 "+tick.instrument+" CTA 策略 "+rule.id+" 开仓: "+playbook.getId());
            asyncSaveState();
        }catch(Throwable t) {
            logger.error("Tradlet group "+group.getId()+" 合约 "+tick.instrument+" CTA 策略 "+rule.id+" 创建交易剧本失败: ", t);
        }
    }

    /**
     * 尝试关闭Playbook
     */
    private void tryClosePlaybooks(MarketData tick) {
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
                CTABreakRule rule = ctaRulesById.get(ctaRuleId);
                if ( null!=rule ) {
                    if ( rule.matchStop(tick) ) {
                        closeReq = new PlaybookCloseReq();
                        closeReq.setActionId("stopLoss@"+PriceUtil.long2str(rule.stop));
                    }
                    if ( rule.matchTake(tick)) {
                        closeReq = new PlaybookCloseReq();
                        closeReq.setActionId("takeProfit@"+PriceUtil.long2str(rule.take));
                    }
                    if ( rule.matchEnd(tick)) {
                        closeReq = new PlaybookCloseReq();
                        closeReq.setActionId("ruleEnd@"+PriceUtil.long2str(tick.lastPrice));
                    }
                }
                if ( null!=closeReq ) {
                    playbookKeeper.closePlaybook(pb, closeReq);
                    logger.info("Tradlet group "+group.getId()+" 合约 "+tick.instrument+" CTA 策略 "+rule.id+" 平仓: "+pb.getId());
                }
            }
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
        List<CTAHint> hints = CTAHint.loadHints(hintFile, tradingDay);
        Set<Exchangeable> instruments = new TreeSet<>();
        Set<String> hintIds = new TreeSet<>();
        Map<Exchangeable, List<CTAHint>> ctaHintsByInstrument = new HashMap<>();
        Map<String, CTABreakRule> ctaRulesById = new HashMap<>();
        for(CTAHint hint:hints) {
            if ( null!=context ) {
                context.addInstrument(hint.instrument);
            }
            instruments.add(hint.instrument);
            List<CTAHint> hints0 = ctaHintsByInstrument.get(hint.instrument);
            if ( null==hints0 ) {
                hints0 = new ArrayList<>();
                ctaHintsByInstrument.put(hint.instrument, hints0);
            }
            for(CTABreakRule policy:hint.rules) {
                ctaRulesById.put(policy.id, policy);
            }
            hintIds.add(hint.id);
            hints0.add(hint);
        }
        this.ctaHintsByInstrument = ctaHintsByInstrument;
        this.ctaRulesById = ctaRulesById;
        logger.info("Group "+group.getId()+" 加载CTA策略, 合约: "+instruments+", 策略ID: "+hintIds);
    }

    @Override
    public void onFileChanged(File file) {
        try{
            reloadHints(null);
        }catch(Throwable t) {
            logger.error("CTA 策略文件 "+file+" 重新加载失败", t);
        }
    }

    /**
     * 从数据库恢复状态
     */
    private void restoreState() {
        String jsonStr = repository.load(BOEntityType.Default, entityId);
        if ( !StringUtil.isEmpty(jsonStr) ) {
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
            JsonArray array = json.get("usedPolicyIds").getAsJsonArray();
            List list = (List)JsonUtil.json2value(array);
            usedPolicyIds = new TreeSet<>(list);
        }
    }

    /**
     * 异步保存状态
     */
    private void asyncSaveState() {
        JsonObject json = new JsonObject();
        json.add("usedPolicyIds", JsonUtil.object2json(usedPolicyIds));
        repository.asynSave(BOEntityType.Default, entityId, json);
    }

}
