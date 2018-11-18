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

import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.service.TraderHomeTestUtil;
import trader.service.md.MarketData;
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

    @Test
    public void test() throws Exception
    {
        LocalDate tradingDay = LocalDate.of(2018,  Month.OCTOBER, 11);
        LocalDateTime beginTime = LocalDateTime.of(2018, Month.OCTOBER, 11, 8, 50);
        LocalDateTime endTime = LocalDateTime.of(2018, Month.OCTOBER, 11, 15, 04);
        Exchangeable ru1901 = Exchangeable.fromString("ru1901");
        SimBeansContainer beansContainer = new SimBeansContainer();
        SimMarketTimeService marketTime = new SimMarketTimeService();
        SimMarketDataService mdService = new SimMarketDataService();

        marketTime.setTimeRange(tradingDay, beginTime, endTime);

        beansContainer.addBean(MarketTimeService.class, marketTime);
        beansContainer.addBean(MarketDataService.class, mdService);

        mdService.addSubscriptions(Arrays.asList(new Exchangeable[] {ru1901}));
        mdService.init(beansContainer);

        TAServiceImpl taService = new TAServiceImpl();
        taService.init(beansContainer);

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
