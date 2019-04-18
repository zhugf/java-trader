package trader.service.ta.bar;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.MarketTimeStage;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.ta.TimeSeriesLoader;
import trader.simulator.SimBeansContainer;
import trader.simulator.SimMarketDataService;

public class WindowTicksBarTest {
    final static Exchangeable ru1901 = Exchangeable.fromString("ru1901");
    final static LocalDate tradingDay = DateUtil.str2localdate("20181203");

    private List<MarketData> ticks = loadTicks(ru1901, tradingDay);

    @Test
    public void testTimedWindow() throws Exception
    {
        TimedWindowTicksBar timedBar = new TimedWindowTicksBar(10*1000);
        for(MarketData tick:ticks) {
            if ( tick.mktStage!=MarketTimeStage.MarketOpen ) {
                continue;
            }
            timedBar.update(tick);
            System.out.println(timedBar.getBeginTime()+" VOL "+timedBar.getVolume().doubleValue());
            assertTrue(PriceUtil.long2price(tick.lastPrice)==timedBar.getClosePrice().doubleValue());
            assertTrue(timedBar.getTimePeriod().getSeconds()<=10);
        }
        assertTrue(ticks.size()>0);
    }


    @Test
    public void testVolWindow() throws Exception
    {
        VolumeWindowTicksBar timedBar = new VolumeWindowTicksBar(1000);
        for(MarketData tick:ticks) {
            if ( tick.mktStage!=MarketTimeStage.MarketOpen ) {
                continue;
            }
            timedBar.update(tick);
            System.out.println(timedBar.getBeginTime()+" VOL "+timedBar.getVolume().doubleValue());
            assertTrue(PriceUtil.long2price(tick.lastPrice)==timedBar.getClosePrice().doubleValue());
            System.out.println(timedBar.getBeginTime()+" VOL "+timedBar.getVolume().doubleValue()+" TIME "+timedBar.getTimePeriod().getSeconds());
        }
        assertTrue(ticks.size()>0);
    }

    private static List<MarketData> loadTicks(Exchangeable ru1901, LocalDate tradingDay)
    {
        try {
            SimBeansContainer beansContainer = new SimBeansContainer();
            final SimMarketDataService mdService = new SimMarketDataService();
            mdService.init(beansContainer);
            beansContainer.addBean(MarketDataService.class, mdService);

            ExchangeableData data = TraderHomeUtil.getExchangeableData();
            TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);
            loader
                .setExchangeable(ru1901)
                .setStartTradingDay(tradingDay)
                .setEndTradingDay(tradingDay)
                .setLevel(PriceLevel.MIN1);

            return loader.loadMarketDataTicks(tradingDay, ExchangeableData.TICK_CTP);
        }catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

}
