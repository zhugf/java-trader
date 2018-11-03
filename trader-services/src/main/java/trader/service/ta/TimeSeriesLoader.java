package trader.service.ta;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.exchangeable.MarketTimeStage;
import trader.common.exchangeable.TradingMarketInfo;
import trader.common.tick.PriceLevel;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVUtil;
import trader.common.util.DateUtil;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketData;
import trader.service.md.ctp.CtpMarketDataProducer;

/**
 * 行情数据加载和转换为分钟级别数据
 */
public class TimeSeriesLoader {

    private ExchangeableData data;
    private Exchangeable exchangeable;
    private PriceLevel level;
    private LocalDate beginDate;
    private LocalDate endDate;

    public TimeSeriesLoader(ExchangeableData data) {
        this.data = data;
    }

    public TimeSeriesLoader setExchangeable(Exchangeable e) {
        this.exchangeable = e;
        return this;
    }

    /**
     * 设置目标级别
     */
    public TimeSeriesLoader setLevel(PriceLevel level) {
        this.level = level;
        return this;
    }

    public TimeSeriesLoader setBeginDate(LocalDate d){
        this.beginDate = d;
        return this;
    }

    /**
     * 设置结束日期, 缺省为当天
     */
    public TimeSeriesLoader setEndDate(LocalDate d){
        this.endDate = d;
        return this;
    }

    /**
     * 加载数据
     */
    public TimeSeries load() throws IOException {
        if ( level==PriceLevel.DAY ) {
            return loadDaySeries();
        }
        if ( endDate==null ) {
            endDate = LocalDate.now();
        }

        List<Bar> min1Bars = new ArrayList<>();
        LocalDate actionDay = beginDate;
        boolean needMerge=false;
        while(actionDay.compareTo(endDate)<=0) {
            if ( data.exists(exchangeable, ExchangeableData.MIN1, actionDay) ) {
                min1Bars.addAll(loadMin1Bars(actionDay));
                needMerge=true;
            }else {
                min1Bars.addAll(loadMinFromTicks(actionDay));
            }
            actionDay = MarketDayUtil.nextMarketDay(exchangeable.exchange(), actionDay);
        }

        //转换MIN1为MIN3-5等等
        List<Bar> bars = min1Bars;
        if ( needMerge && level!=PriceLevel.MIN1 ) {
            bars = mergeMin1Bars(min1Bars);
        }
        BaseTimeSeries result = new BaseTimeSeries(exchangeable.name()+"-"+level, LongNum::valueOf);
        for(Bar bar:bars) {
            result.addBar(bar);
        }
        return result;
    }

    /**
     * 将1分钟K线合并为多分钟K线
     */
    private List<Bar> mergeMin1Bars(List<Bar> min1Bars) {
        List<Bar> result = new ArrayList<>();

        int minutes = level.getMinutePeriod();
        List<Bar> levelBars = new ArrayList<>();
        for(Bar bar:min1Bars) {
            levelBars.add(bar);
            if ( levelBars.size()>=minutes ) {
                result.add(merge(levelBars));
                levelBars.clear();
            }
        }
        return result;
    }

    private Bar merge(List<Bar> bars) {
        Bar first = bars.get(0);
        Bar last=bars.get(bars.size()-1);
        Num open=first.getOpenPrice();
        Num max=first.getMaxPrice();
        Num min=first.getMinPrice();
        Num close=first.getClosePrice();
        Num volume = first.getVolume();
        Num amount=first.getAmount();
        for(int i=1;i<bars.size();i++) {
            Bar bar = bars.get(i);
            max = max.max(bar.getMaxPrice());
            min = min.min(bar.getMinPrice());
            volume = volume.plus(bar.getVolume());
            amount = amount.plus(bar.getAmount());
            close = bar.getClosePrice();
        }
        Bar result = new BaseBar(DateUtil.between(first.getBeginTime().toLocalDateTime(), last.getEndTime().toLocalDateTime()),
        last.getEndTime(),
        open,
        max,
        min,
        close,
        volume,
        amount);

        return result;
    }

    /**
     * 加载某日的MIN1数据
     */
    private List<Bar> loadMin1Bars(LocalDate actionDay) throws IOException {
        List<Bar> result = new ArrayList<>();
        ZoneId zoneId = exchangeable.exchange().getZoneId();
        String csv = data.load(exchangeable, ExchangeableData.MIN1, actionDay);
        CSVDataSet csvDataSet = CSVUtil.parse(csv);
        while(csvDataSet.next()) {
            LocalDateTime beginTime = csvDataSet.getDateTime(ExchangeableData.COLUMN_BEGIN_TIME);
            LocalDateTime endTime = csvDataSet.getDateTime(ExchangeableData.COLUMN_END_TIME);
            ZonedDateTime zonedBeginTime = beginTime.atZone(zoneId);
            ZonedDateTime zonedEndTime = endTime.atZone(zoneId);
            FutureBar bar = new FutureBar(DateUtil.between(beginTime, endTime),
                zonedEndTime,
                new LongNum(csvDataSet.getPrice(ExchangeableData.COLUMN_OPEN)),
                new LongNum(csvDataSet.getPrice(ExchangeableData.COLUMN_HIGH)),
                new LongNum(csvDataSet.getPrice(ExchangeableData.COLUMN_LOW)),
                new LongNum(csvDataSet.getPrice(ExchangeableData.COLUMN_CLOSE)),
                new LongNum(csvDataSet.getInt(ExchangeableData.COLUMN_VOLUME)),
                new LongNum(csvDataSet.getPrice(ExchangeableData.COLUMN_TURNOVER)),
                new LongNum(csvDataSet.getLong(ExchangeableData.COLUMN_OPENINT)));
            result.add(bar);
        }
        return result;
    }

    /**
     * 加载某日的TICK数据, 转换为MIN1数据
     */
    private List<Bar> loadMinFromTicks(LocalDate actionDay) throws IOException {
        List<Bar> result = new ArrayList<>();
        List<MarketData> marketDatas = new ArrayList<>();
        if ( exchangeable.getType()==ExchangeableType.FUTURE ) {
            marketDatas = loadCtpTicks(actionDay);
        }
        return marketDatas2bars(exchangeable, level, marketDatas);
    }

    /**
     * 加载日线数据
     */
    private TimeSeries loadDaySeries() throws IOException
    {
        return null;
    }

    private List<MarketData> loadCtpTicks(LocalDate actionDay) throws IOException
    {
        List<MarketData> result = new ArrayList<>();
        CtpMarketDataProducer mdProducer = new CtpMarketDataProducer();
        CtpCSVMarshallHelper csvMarshallHelper = new CtpCSVMarshallHelper();
        String csv = data.load(exchangeable, ExchangeableData.TICK_CTP, actionDay);
        CSVDataSet csvDataSet = CSVUtil.parse(csv);
        while(csvDataSet.next()) {
            MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), actionDay);
            result.add(marketData);
        }
        return result;
    }

    /**
     * 将原始CTP TICK转为MIN1 Bar
     */
    public static List<Bar> marketDatas2bars(Exchangeable exchangeable, PriceLevel level ,List<MarketData> marketDatas){
        List<Bar> result = new ArrayList<>();
        MarketData beginTick=marketDatas.get(0), lastTick=null;
        int lastTickIndex=getTickIndex(exchangeable, level, beginTick);
        long high=beginTick.lastPrice, low=beginTick.lastPrice;
        for(int i=0;i<marketDatas.size();i++) {
            MarketData currTick = marketDatas.get(i);
            int currTickIndex = getTickIndex(exchangeable, level, currTick);
            if ( currTickIndex<0 ) {
                continue;
            }
            if ( lastTickIndex!=currTickIndex ) {
                MarketData endTick = currTick;
                if( lastTickIndex>currTickIndex ) { //换了日市夜市
                    endTick = lastTick;
                }
                LocalDateTime endTime = DateUtil.round(endTick.updateTime);
                //创建新的Bar
                FutureBar bar = new FutureBar(DateUtil.between(DateUtil.round(beginTick.updateTime), endTime),
                                endTime.atZone(exchangeable.exchange().getZoneId()),
                                new LongNum(beginTick.lastPrice),
                                new LongNum(high),
                                new LongNum(low),
                                new LongNum(endTick.lastPrice),
                                new LongNum(endTick.volume-beginTick.volume),
                                new LongNum(endTick.turnover-beginTick.turnover),
                                new LongNum(endTick.openInterest)
                                );
                result.add(bar);
                high = low = currTick.lastPrice;
                beginTick = currTick;
                lastTickIndex=currTickIndex;
                continue;
            }
            high = Math.max(high, currTick.lastPrice);
            low = Math.min(low, currTick.lastPrice);
            lastTick = currTick;
        }
        //Convert market data to MIN1
        lastTick = marketDatas.get(marketDatas.size()-1);
        if ( lastTick!=beginTick ) {
            FutureBar bar = new FutureBar(DateUtil.between(beginTick.updateTime, lastTick.updateTime),
                    lastTick.updateTime.atZone(exchangeable.exchange().getZoneId()),
                    new LongNum(beginTick.lastPrice),
                    new LongNum(high),
                    new LongNum(low),
                    new LongNum(lastTick.lastPrice),
                    new LongNum(lastTick.volume-beginTick.volume),
                    new LongNum(lastTick.turnover-beginTick.turnover),
                    new LongNum(lastTick.openInterest)
                    );
            result.add(bar);
        }
        return result;
    }

    /**
     * Return the tick index for a market data point
     */
    private static int getTickIndex(Exchangeable exchangeable, PriceLevel level, MarketData currTick)
    {
        if( level.ordinal()>=PriceLevel.DAY.ordinal() ){
            return 0;
        }
        LocalDateTime marketTime = currTick.updateTime;
        TradingMarketInfo marketInfo = exchangeable.detectTradingMarketInfo(marketTime);
        if ( marketInfo==null ) {
            return -1;
        }
        int tradingMS = exchangeable.getTradingMilliSeconds(marketInfo.getMarket(), marketTime);
        int tradeMinutes = tradingMS / (1000*60);
        MarketTimeStage mts = exchangeable.getTimeStage(marketInfo.getMarket(), marketInfo.getTradingDay(), marketTime);
        switch(mts){
        case AggregateAuction:
        case BeforeMarketOpen:
            return -1;
        case MarketOpen:
            //lastMarketOpenMinutes = tradeMinutes;
        case MarketBreak:
        case MarketClose:
            int msLeft = marketInfo.getTradingSeconds()*1000-tradingMS;
            int tickIndex = tradeMinutes/level.getMinutePeriod();
            if ( msLeft<=0 ){
                //09:01:00.000 - 09:01:00.999
                tickIndex--;
            }
            return tickIndex;
        default:
            return -1;
        }
    }

    /**
     * 返回交易时间, 131000 格式
     */
    protected static int getMarketTime(LocalTime marketTime){
        if ( marketTime==null ){
            return 0;
        }
        int marketHour = marketTime.getHour();
        int marketMinute = marketTime.getMinute();
        int marketSecond = marketTime.getSecond();
        int time = marketHour*10000+marketMinute*100+marketSecond;
        return time;
    }

}
