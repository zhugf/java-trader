package trader.service.ta.trend;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeHelper;
import trader.service.log.LogServiceImpl;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.ta.LongNum;
import trader.service.ta.TimeSeriesLoader;
import trader.service.ta.bar.FutureBarBuilder;
import trader.service.ta.trend.WaveBar.WaveType;
import trader.simulator.SimBeansContainer;
import trader.simulator.SimMarketDataService;

public class WaveBar2BuilderTest {

    static {
        LogServiceImpl.setLogLevel("org.reflections", "INFO");
        LogServiceImpl.setLogLevel("trader", "INFO");
        LogServiceImpl.setLogLevel("org.apache.commons", "INFO");

        TraderHomeHelper.init(null);
    }

    @Test
    public void test() throws Exception
    {
        SimBeansContainer beansContainer = new SimBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);

        LocalDate beginDate = DateUtil.str2localdate("20181114");
        LocalDate endDate = DateUtil.str2localdate("20181221");
        LocalDate tradingDay = beginDate;
        Exchangeable e = Exchangeable.fromString("ru1901");

        while(tradingDay.compareTo(endDate)<=0) {
            List<MarketData> ticks = loader.setExchangeable(e).loadMarketDataTicks(tradingDay, ExchangeableData.TICK_CTP);
            if ( !ticks.isEmpty() ) {
                PriceLevel level = PriceLevel.resolveVolDaily(ticks.get(0).openInterest, 500);

                ExchangeableTradingTimes tradingTimes = e.exchange().getTradingTimes(e, tradingDay);
                FutureBarBuilder barBuilder = new FutureBarBuilder(tradingTimes, level);
                WaveBar2Builder waveBar2Builder = new WaveBar2Builder(barBuilder);
                waveBar2Builder.getOption().strokeThreshold = LongNum.fromRawValue(e.getPriceTick()*4);

                for(MarketData tick:ticks) {
                    waveBar2Builder.update(tick);
                }

                List<WaveBar> strokeBars = waveBar2Builder.getBars(WaveType.Stroke);
                List<WaveBar> sectionBars = waveBar2Builder.getBars(WaveType.Section);
                assertTrue(strokeBars.size()>2);
                if ( strokeBars.size()>3 ) {
                    assertTrue(sectionBars.size()>=1);
                }
                for(WaveBar bar:sectionBars) {
                    System.out.println(bar);
                    for(Object bar0:bar.getBars()) {
                        System.out.println("\t"+bar0);
                    }
                }

                System.out.println();
            }
            tradingDay = MarketDayUtil.nextMarketDay(Exchange.DCE, tradingDay);
        }
    }

}
