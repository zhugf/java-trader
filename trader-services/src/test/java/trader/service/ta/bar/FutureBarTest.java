package trader.service.ta.bar;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.google.gson.JsonElement;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.JsonUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeHelper;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.ta.BaseLeveledBarSeries;
import trader.service.ta.FutureBarImpl;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.BarSeriesLoader;
import trader.service.util.SimpleBeansContainer;
import trader.simulator.SimMarketDataService;

public class FutureBarTest {

    static {
        TraderHomeHelper.init(null);
    }

    public void testVolBars() throws Exception {
        Exchangeable e = Exchangeable.fromString("ru1901");

        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);

        LocalDate beginDate = DateUtil.str2localdate("20181114");
        LocalDate endDate = DateUtil.str2localdate("20181130");

        LocalDate tradingDay = beginDate;
        int totalBarCount = 0, totalDays = 0;
        while(tradingDay.isBefore(endDate)) {
            List<MarketData> ticks = loader
            .setInstrument(Exchangeable.fromString("ru1901"))
            .loadMarketDataTicks(tradingDay, ExchangeableData.TICK_CTP);
            if ( !ticks.isEmpty() ) {
                ExchangeableTradingTimes tradingTimes = e.exchange().getTradingTimes(e, tradingDay);
                PriceLevel level = PriceLevel.resolveVolDaily(ticks.get(0).openInterest, 500);
                FutureBarBuilder barBuilder = new FutureBarBuilder(tradingTimes, level);
                long volume = 0, openInt=0;
                for(MarketData tick:ticks) {
                    barBuilder.update(tick);
                    volume = tick.volume;
                    openInt = tick.openInterest;
                }
                int barCount = barBuilder.getTimeSeries(level).getBarCount();
                LeveledBarSeries series = barBuilder.getTimeSeries(level);
                List<Integer> barSeconds = new ArrayList<>();
                for(int i=0;i<series.getBarCount();i++) {
                    int barSecond = (int)series.getBar(i).getTimePeriod().getSeconds();
                    //System.out.println("\t"+barSecond);
                    barSeconds.add(barSecond);
                }
                //找前20%的平均
                Collections.sort(barSeconds);
                Object[] avg = new Object[5];
                for(int i=0;i<5;i++) {
                    avg[i] = getAvg(barSeconds.subList(barCount/5*i, barCount/5*(i+1)));
                }
                System.out.println(e+" "+tradingDay+" barCount "+barCount+" lastVolume: "+volume+" openInt: "+openInt+", bar level: "+level+", avg per 20% "+Arrays.asList(avg));

                totalBarCount += barCount;
                totalDays++;
            }
            tradingDay = MarketDayUtil.nextMarketDay(e.exchange(), tradingDay);
        }
        System.out.println("Total days: "+totalDays+" avg barCount: "+ (totalBarCount/totalDays));
    }

    private double getAvg(List<Integer> list) {
        long total = 0;
        for(int i:list) {
            total += i;
        }
        double avgFirst20 = total/list.size();
        return avgFirst20;
    }

    @Test
    public void testFutureBarsJson() throws Exception {
        Exchangeable e = Exchangeable.fromString("ru1901");

        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);

        LocalDate endDate = DateUtil.str2localdate("20181130");
        LeveledBarSeries series = loader
        .setInstrument(e)
        .setStartTradingDay(endDate).setEndTradingDay(endDate).setLevel(PriceLevel.MIN1).load();
        FutureBarImpl bar = (FutureBarImpl)series.getBar(0);
        JsonElement json = JsonUtil.object2json(bar);
        FutureBarImpl bar2 = FutureBarImpl.fromJson(e, json);

        assertTrue(bar.getBeginTime().equals(bar2.getBeginTime()));
        assertTrue(bar.getEndTime().equals(bar2.getEndTime()));
        assertTrue(bar.getAmount().equals(bar2.getAmount()));
        assertTrue(bar.getVolume().equals(bar2.getVolume()));
        assertTrue(bar.getOpenPrice().equals(bar2.getOpenPrice()));
        assertTrue(bar.getClosePrice().equals(bar2.getClosePrice()));
        assertTrue(bar.getHighPrice().equals(bar2.getHighPrice()));
        assertTrue(bar.getLowPrice().equals(bar2.getLowPrice()));

        assertTrue(bar2.getBeginAmount().equals(bar.getBeginAmount()));
        assertTrue(bar2.getBeginVolume().equals(bar.getBeginVolume()));
        assertTrue(bar2.getOpenInt() == (bar.getOpenInt()));

        JsonElement barsJson = JsonUtil.object2json(series);

        BaseLeveledBarSeries series2 = BaseLeveledBarSeries.fromJson(null, barsJson);
        assertTrue(series2.getBarCount()==series.getBarCount());
    }

}
