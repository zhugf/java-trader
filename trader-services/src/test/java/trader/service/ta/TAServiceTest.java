package trader.service.ta;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.service.TraderHomeTestUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataService;
import trader.service.trade.MarketTimeService;
import trader.simulator.SimBeansContainer;
import trader.simulator.SimMarketDataService;
import trader.simulator.SimMarketTimeService;

public class TAServiceTest {

    @Before
    public void setup() {
        TraderHomeTestUtil.initRepoistoryDir();
    }

    private static class MyTAListener implements TAListener, MarketDataListener {

        MACDIndicator macdIndicator;
        EMAIndicator deaIndicator;
        LeveledTimeSeries min1Series = null;

        private void createIndicators(LeveledTimeSeries series) {
            min1Series = series;
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            macdIndicator = new MACDIndicator(closePrice, 12, 26);
            deaIndicator = new EMAIndicator(macdIndicator, 9);
        }

        @Override
        public void onNewBar(Exchangeable e, LeveledTimeSeries series) {
            if ( series.getLevel()==PriceLevel.MIN1 ) {
                if ( macdIndicator==null ) {
                    createIndicators(series);
                }
                System.out.println("NEW MIN1 Bar: "+series.getLastBar());
            }
        }

        @Override
        public void onMarketData(MarketData marketData) {
            if ( macdIndicator==null ) {
                return;
            }
            int lastIndex = min1Series.getEndIndex();
            System.out.println("MACD(MIN1, 0): "+macdIndicator.getValue(lastIndex));
        }

    }

    @Test
    public void test() throws Exception
    {
        LocalDate tradingDay = LocalDate.of(2018,  Month.OCTOBER, 11);
        LocalDateTime beginTime = LocalDateTime.of(2018, Month.OCTOBER, 11, 8, 50);
        LocalDateTime endTime = LocalDateTime.of(2018, Month.OCTOBER, 11, 15, 04);
        Exchangeable ru1901 = Exchangeable.fromString("ru1901");
        final SimBeansContainer beansContainer = new SimBeansContainer();
        final SimMarketTimeService marketTime = new SimMarketTimeService();
        final SimMarketDataService mdService = new SimMarketDataService();
        final MyTAListener myTAListener = new MyTAListener();
        marketTime.setTimeRange(tradingDay, beginTime, endTime);

        beansContainer.addBean(MarketTimeService.class, marketTime);
        beansContainer.addBean(MarketDataService.class, mdService);

        mdService.addSubscriptions(Arrays.asList(new Exchangeable[] {ru1901}));
        mdService.init(beansContainer);
        TAServiceImpl taService = new TAServiceImpl();
        taService.init(beansContainer);
        taService.addListener(myTAListener);
        mdService.addListener(myTAListener, ru1901);
        //时间片段循环
        while(marketTime.nextTimePiece());
        MarketData lastTick = mdService.getLastData(ru1901);
        TimeSeries min1Series = taService.getSeries(ru1901, PriceLevel.MIN1);
        Bar lastMin1Bar= min1Series.getLastBar();
        assertTrue(lastMin1Bar.getBeginTime().toLocalDateTime().getMinute()==59);
        assertTrue(lastMin1Bar.getEndTime().toLocalDateTime().equals(lastTick.updateTime));
        assertTrue(marketTime.getMarketTime().equals(endTime));

        TimeSeries min3Series = taService.getSeries(ru1901, PriceLevel.MIN3);
        Bar lastMin3Bar = min3Series.getLastBar();
        assertTrue(lastMin3Bar.getEndTime().toLocalDateTime().getMinute()==0);
    }

}
