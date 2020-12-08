package trader.service.tradlet.impl;

import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.service.md.MarketData;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.LongNum;
import trader.service.ta.TechnicalAnalysisAccess;
import trader.service.ta.TechnicalAnalysisService;
import trader.service.ta.indicators.MACDIndicator;
import trader.service.trade.Order;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.trade.Transaction;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.PlaybookBuilder;
import trader.service.tradlet.PlaybookCloseReq;
import trader.service.tradlet.PlaybookKeeper;
import trader.service.tradlet.PlaybookStateTuple;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletContext;
import trader.service.tradlet.TradletGroup;

/**
 * MACD 1-3-5 策略
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "MACD135")
public class MACD135Tradlet implements Tradlet {
    private final static Logger logger = LoggerFactory.getLogger(MACD135Tradlet.class);

    public static final String OPEN_LONG_ACTION = "MACD135-Open-Long";
    public static final String OPEN_SHORT_ACTION = "MACD135-Open-Short";

    public static final String CLOSE_LONG_ACTION = "MACD135-Close-Long";
    public static final String CLOSE_SHORT_ACTION = "MACD135-Close-Short";

    private BeansContainer beansContainer;
    private TradletGroup group;
    private TechnicalAnalysisService taService;
    private PlaybookKeeper playbookKeeper;
    private Playbook activePlaybook;

    private BarSeries min1Series;
    private BarSeries min3Series;
    private BarSeries min5Series;

    private MACDIndicator min1MACD;
    private org.ta4j.core.indicators.MACDIndicator min1DIFF;
    private MACDIndicator min3MACD;
    private org.ta4j.core.indicators.MACDIndicator min3DIFF;
    private MACDIndicator min5MACD;
    private org.ta4j.core.indicators.MACDIndicator min5DIFF;

    private Properties props = new Properties();

    @Override
    public void init(TradletContext context) throws Exception {
        beansContainer = context.getBeansContainer();
        group = context.getGroup();
        props.putAll(context.getConfigAsProps());
        Exchangeable instrument = group.getInstruments().get(0);
        playbookKeeper = group.getPlaybookKeeper();
        taService = beansContainer.getBean(TechnicalAnalysisService.class);

        TechnicalAnalysisAccess item = taService.forInstrument(instrument);
        min1Series = item.getSeries(PriceLevel.MIN1);
        min3Series = item.getSeries(PriceLevel.MIN3);
        min5Series = item.getSeries(PriceLevel.MIN5);

        ClosePriceIndicator min1ClosePrice = new ClosePriceIndicator(min1Series);
        ClosePriceIndicator min3ClosePrice = new ClosePriceIndicator(min3Series);
        ClosePriceIndicator min5ClosePrice = new ClosePriceIndicator(min5Series);

        min1MACD = new MACDIndicator(min1ClosePrice);
        min3MACD = new MACDIndicator(min3ClosePrice);
        min5MACD = new MACDIndicator(min5ClosePrice);

        min1DIFF = new org.ta4j.core.indicators.MACDIndicator(min1ClosePrice, 12, 26);
        min3DIFF = new org.ta4j.core.indicators.MACDIndicator(min3ClosePrice, 12, 26);
        min5DIFF = new org.ta4j.core.indicators.MACDIndicator(min5ClosePrice, 12, 26);
    }

    @Override
    public void reload(TradletContext context) throws Exception {

    }

    @Override
    public void destroy() {

    }

    @Override
    public Object onRequest(String path, Map<String, String> params, String payload) {
        return null;
    }

    @Override
    public void onTick(MarketData marketData) {
        int hhmmss = DateUtil.time2int(marketData.updateTime.toLocalTime());
        //09:00:00-09:00:00不开仓
        if ( hhmmss>=90000 && hhmmss<= 91000 ) {
            return;
        }
        //防止重复开仓
        if ( activePlaybook!=null ) {
            closePlaybook();
        }else {
            createPlaybook();
        }
    }

    @Override
    public void onTransaction(Order order, Transaction txn) {
    }

    @Override
    public void onNewBar(LeveledBarSeries series) {
    }

    @Override
    public void onNoopSecond() {

    }

    @Override
    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple) {
        //当前活动playbook已结束, 用于仓位止损时得到通知
        if ( playbook==activePlaybook && playbook.getStateTuple().getState().isDone() ) {
            activePlaybook = null;
        }
    }

    /**
     * 为当前仓位主动平仓
     */
    private void closePlaybook() {
        String closeAction = null;
        if (activePlaybook.getDirection()==PosDirection.Long) {
            //平多: 二、3F进入第三阶段，1F进入第三阶段平仓。
            if ( canCloseLong() ){
                closeAction =CLOSE_LONG_ACTION;
            }
        }else {
            //平空
            if ( canCloseShort() ) {
                closeAction =CLOSE_SHORT_ACTION;
            }
        }

        if ( closeAction!=null ) {
            PlaybookCloseReq closeReq = new PlaybookCloseReq();
            closeReq.setActionId(closeAction);
            playbookKeeper.closePlaybook(activePlaybook, closeReq);
        }
    }

    /**
     * 根据MACD135规则开仓
     */
    private void createPlaybook() {
        String action = null;
        PosDirection actionDir = null;

        if ( canOpenLong() ) {
            action = OPEN_LONG_ACTION;
            actionDir = PosDirection.Long;
        }else if ( canOpenShort() ) {
            action = OPEN_SHORT_ACTION;
            actionDir = PosDirection.Short;
        }
        if ( action!=null ) {
            PlaybookBuilder builder = new PlaybookBuilder()
                    .setActionId(action)
                    .setOpenDirection(actionDir);
            for(Object key:props.keySet()) {
                String k = key.toString();
                String val = props.getProperty(k);
                if ( k.startsWith("playbook.")) {
                    builder.setAttr(k.substring("playbook.".length()), val);
                }
            }
            try {
                activePlaybook = playbookKeeper.createPlaybook(this, builder);
                activePlaybook.open();
            } catch (Throwable e) {
                logger.error("Tradlet group "+group.getId()+" create playbook for action "+action+" failed: "+e.toString(), e);
                activePlaybook = null;
            }
        }
    }

    /**
     * 是否可以平多.
     * <BR>二、3F进入第三阶段，1F进入第三阶段平仓。
     */
    private boolean canCloseLong() {
        boolean result = false;

        result =
                //MACD(MIN3)<=MACD(MIN3,1)
                levelLongCloseCriteria(min3Series, min3MACD, min3DIFF)
                //MACD(MIN1)<=MACD(MIN1,1)
                && levelLongCloseCriteria(min1Series, min1MACD, min1DIFF);

        return result;
    }

    /**
     * 是否可以平空
     */
    private boolean canCloseShort() {
        boolean result = false;

        result =
                //MACD(MIN3)<=MACD(MIN3,1)
                levelShortCloseCriteria(min3Series, min3MACD, min3DIFF)
                //MACD(MIN1)<=MACD(MIN1,1)
                && levelShortCloseCriteria(min1Series, min1MACD, min1DIFF);

        return result;

    }

    /**
     * 是否可以开多
     */
    private boolean canOpenLong() {
        boolean result = false;

        result =
                //DIFF(MIN5)>=0 && MACD(MIN5)>=MACD(MIN5,1)
                levelLongCriteria(min5Series, min5MACD, min5DIFF)
                //DIFF(MIN3)>=0 && MACD(MIN3)>=MACD(MIN3,1)
                && levelLongCriteria(min3Series, min3MACD, min3DIFF)
                //DIFF(MIN1) > DIFF(MIN1,1)
                && levelDIFF_G_THAN_DIFF1(min1Series, min1DIFF);
        return result;
    }

    /**
     * 是否可以开空
     */
    private boolean canOpenShort() {
        boolean result = false;

        result =
                //DIFF(MIN5)<=0 && MACD(MIN5)<=MACD(MIN5,1)
                levelShortCriteria(min5Series, min5MACD, min5DIFF)
                //DIFF(MIN3)<=0 && MACD(MIN3)<=MACD(MIN3,1)
                && levelShortCriteria(min3Series, min3MACD, min3DIFF)
                //DIFF(MIN1)<DIFF(MIN1,1)
                && levelDIFF_L_THAN_DIFF1(min1Series, min1DIFF);
        return result;
    }

    /**
     * DIFF()<=0 && MACD<=MACD(1)
     */
    private boolean levelLongCloseCriteria(BarSeries levelSeries, MACDIndicator levelMACD, org.ta4j.core.indicators.MACDIndicator levelDIFF)
    {
        boolean result = false;
        int levelLastIndex = levelSeries.getEndIndex();
        if ( levelLastIndex>=1 ) {
            Num levelDIFFValue = levelDIFF.getValue(levelLastIndex);
            Num levelMACD0 = levelMACD.getValue(levelLastIndex);
            Num levelMACD1 = levelMACD.getValue(levelLastIndex-1);
            result = levelDIFFValue.isLessThanOrEqual(LongNum.ZERO) && levelMACD0.isLessThanOrEqual(levelMACD1);
        }
        return result;
    }

    /**
     * DIFF()>=0 && MACD>=MACD(1)
     */
    private boolean levelShortCloseCriteria(BarSeries levelSeries, MACDIndicator levelMACD, org.ta4j.core.indicators.MACDIndicator levelDIFF)
    {
        boolean result = false;
        int levelLastIndex = levelSeries.getEndIndex();
        if ( levelLastIndex>=1 ) {
            Num levelDIFFValue = levelDIFF.getValue(levelLastIndex);
            Num levelMACD0 = levelMACD.getValue(levelLastIndex);
            Num levelMACD1 = levelMACD.getValue(levelLastIndex-1);
            result = levelDIFFValue.isGreaterThanOrEqual(LongNum.ZERO) && levelMACD0.isGreaterThanOrEqual(levelMACD1);
        }
        return result;
    }

    /**
     * 判断 DIFF>DIFF(1)
     */
    private static boolean levelDIFF_G_THAN_DIFF1(BarSeries levelSeries, org.ta4j.core.indicators.MACDIndicator levelDIFF) {
        boolean result = false;
        int levelLastIndex = levelSeries.getEndIndex();
        if ( levelLastIndex>=1 ) {
            Num levelDIFFValue = levelDIFF.getValue(levelLastIndex);
            Num levelDIFFValue0 = levelDIFF.getValue(levelLastIndex-1);
            result = levelDIFFValue.isGreaterThan(levelDIFFValue0);
        }
        return result;
    }

    /**
     * 判断 DIFF<DIFF(1)
     */
    private static boolean levelDIFF_L_THAN_DIFF1(BarSeries levelSeries, org.ta4j.core.indicators.MACDIndicator levelDIFF) {
        boolean result = false;
        int levelLastIndex = levelSeries.getEndIndex();
        if ( levelLastIndex>=1 ) {
            Num levelDIFFValue = levelDIFF.getValue(levelLastIndex);
            Num levelDIFFValue0 = levelDIFF.getValue(levelLastIndex-1);
            result = levelDIFFValue.isLessThan(levelDIFFValue0);
        }
        return result;
    }

    private static boolean levelLongCriteria(BarSeries levelSeries, MACDIndicator levelMACD, org.ta4j.core.indicators.MACDIndicator levelDIFF)
    {
        boolean result = false;
        int levelLastIndex = levelSeries.getEndIndex();
        if ( levelLastIndex>=1 ) {
            Num levelDIFFValue = levelDIFF.getValue(levelLastIndex);
            Num levelMACD0 = levelMACD.getValue(levelLastIndex);
            Num levelMACD1 = levelMACD.getValue(levelLastIndex-1);
            result = levelDIFFValue.isGreaterThanOrEqual(LongNum.ZERO) && levelMACD0.isGreaterThanOrEqual(levelMACD1);
        }
        return result;
    }

    private static boolean levelShortCriteria(BarSeries levelSeries, MACDIndicator levelMACD, org.ta4j.core.indicators.MACDIndicator levelDIFF)
    {
        boolean result = false;
        int levelLastIndex = levelSeries.getEndIndex();
        if ( levelLastIndex>=1 ) {
            Num levelDIFFValue = levelDIFF.getValue(levelLastIndex);
            Num levelMACD0 = levelMACD.getValue(levelLastIndex);
            Num levelMACD1 = levelMACD.getValue(levelLastIndex-1);
            result = levelDIFFValue.isLessThanOrEqual(LongNum.ZERO) && levelMACD0.isLessThanOrEqual(levelMACD1);
        }
        return result;
    }

}
