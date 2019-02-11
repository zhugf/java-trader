package trader.service.tradlet.impl;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.LongNum;
import trader.service.ta.TAService;
import trader.service.ta.indicators.MACDIndicator;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.PlaybookBuilder;
import trader.service.tradlet.PlaybookKeeper;
import trader.service.tradlet.PlaybookStateTuple;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletContext;
import trader.service.tradlet.TradletGroup;

/**
 * 1-3-5 算法
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "MACD135")
public class MACD135Tradlet implements Tradlet {
    private final static Logger logger = LoggerFactory.getLogger(MACD135Tradlet.class);

    public static final String OPEN_LONG_ACTION = "MACD135-Open-Long";
    public static final String OPEN_SHORT_ACTION = "MACD135-Open-Short";

    private BeansContainer beansContainer;
    private TradletGroup group;
    private TAService taService;
    private PlaybookKeeper playbookKeeper;
    private TimeSeries min1Series;
    private TimeSeries min3Series;
    private TimeSeries min5Series;

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
        props.putAll(context.getConfig());
        Exchangeable instrument = group.getExchangeable();
        playbookKeeper = group.getPlaybookKeeper();
        taService = beansContainer.getBean(TAService.class);

        min1Series = taService.getSeries(instrument, PriceLevel.MIN1);
        min3Series = taService.getSeries(instrument, PriceLevel.MIN3);
        min5Series = taService.getSeries(instrument, PriceLevel.MIN5);

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
    public void destroy() {

    }

    @Override
    public void onTick(MarketData marketData) {
        int hhmmss = DateUtil.time2int(marketData.updateTime.toLocalTime());
        if ( hhmmss<= 91500 ) {
            return;
        }
        String action = null;
        PosDirection actionDir = null;
        //防止重复开仓
        if ( playbookKeeper.getActivePlaybooks(OPEN_LONG_ACTION).isEmpty() && canOpenLong() ) {
            action = OPEN_LONG_ACTION;
            actionDir = PosDirection.Long;
        }
        if ( playbookKeeper.getActivePlaybooks(OPEN_SHORT_ACTION).isEmpty() && canOpenShort() ) {
            action = OPEN_SHORT_ACTION;
            actionDir = PosDirection.Short;
        }

        if ( action!=null ) {
            PlaybookBuilder builder = new PlaybookBuilder()
                    .setOpenActionId(action)
                    .setOpenDirection(actionDir);
            for(Object key:props.keySet()) {
                String k = key.toString();
                String val = props.getProperty(k);
                if ( k.startsWith("playbook.")) {
                    builder.setAttr(k.substring("playbook.".length()), val);
                }
            }
            try {
                playbookKeeper.createPlaybook(builder);
            } catch (Throwable e) {
                logger.error("Tradletr group "+group.getId()+" create playbook for action "+action+" failed: "+e.toString(), e);
            }
        }
    }

    @Override
    public void onNewBar(LeveledTimeSeries series) {
    }

    @Override
    public void onNoopSecond() {

    }

    @Override
    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple) {

    }

    /**
     * 是否可以开多
     */
    private boolean canOpenLong() {
        boolean result = false;

        result = levelLongCriteria(min5Series, min5MACD, min5DIFF)
                && levelLongCriteria(min3Series, min3MACD, min3DIFF)
                && levelDIFF_G_THAN_DIFF1(min1Series, min1DIFF);
        return result;
    }

    /**
     * 是否可以开空
     */
    private boolean canOpenShort() {
        boolean result = false;

        result = levelShortCriteria(min5Series, min5MACD, min5DIFF)
                && levelShortCriteria(min3Series, min3MACD, min3DIFF)
                && levelDIFF_L_THAN_DIFF1(min1Series, min1DIFF);
        return result;
    }

    /**
     * 判断 DIFF>DIFF(1)
     */
    private static boolean levelDIFF_G_THAN_DIFF1(TimeSeries levelSeries, org.ta4j.core.indicators.MACDIndicator levelDIFF) {
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
    private static boolean levelDIFF_L_THAN_DIFF1(TimeSeries levelSeries, org.ta4j.core.indicators.MACDIndicator levelDIFF) {
        boolean result = false;
        int levelLastIndex = levelSeries.getEndIndex();
        if ( levelLastIndex>=1 ) {
            Num levelDIFFValue = levelDIFF.getValue(levelLastIndex);
            Num levelDIFFValue0 = levelDIFF.getValue(levelLastIndex-1);
            result = levelDIFFValue.isLessThan(levelDIFFValue0);
        }
        return result;
    }

    private static boolean levelLongCriteria(TimeSeries levelSeries, MACDIndicator levelMACD, org.ta4j.core.indicators.MACDIndicator levelDIFF)
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

    private static boolean levelShortCriteria(TimeSeries levelSeries, MACDIndicator levelMACD, org.ta4j.core.indicators.MACDIndicator levelDIFF)
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
