package trader.service.ta;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.tick.PriceLevel;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.TraderHomeHelper;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataService;
import trader.service.trade.MarketTimeService;
import trader.service.util.SimpleBeansContainer;
import trader.simulator.SimMarketDataService;
import trader.simulator.SimMarketTimeService;

/**
 * 测试技术指标的实时计算
 */
public class TAServiceTest {

    static {
        TraderHomeHelper.init(null);
    }

    @Test
    public void testSubscriptions() {
        String filter = "   fu ; ru, au\n\tag";
        String[] strs = StringUtil.split(filter, ",|;|\\s");
        assertTrue(strs.length==4);
    }

    @Test
    public void ru1901_MACD() throws Exception
    {
        LocalDate tradingDay = LocalDate.of(2018,  Month.DECEMBER, 3);
        Exchangeable ru1901 = Exchangeable.fromString("ru1901");
        final SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketTimeService marketTime = new SimMarketTimeService();
        final SimMarketDataService mdService = new SimMarketDataService();
        final MyMACDListener myTAListener = new MyMACDListener();
        ExchangeableTradingTimes tradingTimes = ru1901.exchange().getTradingTimes(ru1901, tradingDay);
        marketTime.setTimeRanges(tradingDay, tradingTimes.getMarketTimes() );

        beansContainer.addBean(MarketTimeService.class, marketTime);
        beansContainer.addBean(MarketDataService.class, mdService);

        mdService.addSubscriptions(Arrays.asList(new Exchangeable[] {ru1901}));
        mdService.init(beansContainer);
        TAServiceImpl taService = new TAServiceImpl();
        taService.addSubscriptions("au","ru");
        taService.init(beansContainer);
        taService.registerListener(Arrays.asList(ru1901), Arrays.asList(PriceLevel.MIN1, PriceLevel.MIN3), myTAListener);
        mdService.addListener(myTAListener, ru1901);
        //时间片段循环
        while(marketTime.nextTimePiece());
        MarketData lastTick = mdService.getLastData(ru1901);
        TAItem item = taService.getItem(ru1901);
        TimeSeries min1Series = item.getSeries(PriceLevel.MIN1);
        Bar lastMin1Bar= min1Series.getLastBar();
        assertTrue(lastMin1Bar.getBeginTime().toLocalDateTime().getMinute()==59);
        assertTrue(lastMin1Bar.getEndTime().toLocalDateTime().equals(lastTick.updateTime));

        TimeSeries min3Series = item.getSeries(PriceLevel.MIN3);
        Bar lastMin3Bar = min3Series.getLastBar();
        //assertTrue(lastMin3Bar.getEndTime().toLocalDateTime().getMinute()==0);
    }

}


/**
 * 测试MACD计算
 */
class MyMACDListener implements TAListener, MarketDataListener {
    LeveledTimeSeries min1Series = null;
    org.ta4j.core.indicators.MACDIndicator diffIndicator;
    EMAIndicator deaIndicator;
    trader.service.ta.indicators.MACDIndicator min1MACDIndicator;

    private double min1Macd(int index) {
        double diff = diffIndicator.getValue(index).doubleValue();
        double dea = deaIndicator.getValue(index).doubleValue();

        return PriceUtil.double2price((diff-dea)*2);
    }

    private void createIndicators(LeveledTimeSeries series) {
        min1Series = series;
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        diffIndicator = new org.ta4j.core.indicators.MACDIndicator(closePrice, 12, 26);
        deaIndicator = new EMAIndicator(diffIndicator, 9);
        min1MACDIndicator = new trader.service.ta.indicators.MACDIndicator(closePrice);
    }

    @Override
    public void onNewBar(Exchangeable e, LeveledTimeSeries series) {
        if ( series.getLevel()==PriceLevel.MIN1 ) {
            if ( diffIndicator==null ) {
                createIndicators(series);
            }
        }
    }

    @Override
    public void onMarketData(MarketData marketData) {
        if ( diffIndicator==null ) {
            return;
        }
        int lastIndex = min1Series.getEndIndex();
        assertTrue( Math.abs(min1Macd(lastIndex-1)-min1MACDIndicator.getValue(lastIndex-1).doubleValue())<=0.00011);
        assertTrue( Math.abs(min1Macd(lastIndex)-min1MACDIndicator.getValue(lastIndex).doubleValue())<=0.00011);
        System.out.println("MACD(MIN1, 1): "+min1Macd(lastIndex-1)+", "+min1MACDIndicator.getValue(lastIndex-1));
        System.out.println("MACD(MIN1, 0): "+min1Macd(lastIndex)+", "+min1MACDIndicator.getValue(lastIndex));
    }

}
