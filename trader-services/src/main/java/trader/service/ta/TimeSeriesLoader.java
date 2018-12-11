package trader.service.ta;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
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
    /**
     * 起始交易日
     */
    private LocalDate startTradingDay;
    /**
     * 结束交易日
     */
    private LocalDate endTradingDay;
    /**
     * 实际时间
     */
    private LocalDateTime endTime;
    private List<LocalDate> loadedDates = new ArrayList<>();

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

    /**
     * 设置第一个交易日, 缺省为当天
     */
    public TimeSeriesLoader setStartTradingDay(LocalDate d){
        this.startTradingDay = d;
        return this;
    }

    /**
     * 设置最后一个交易日, 缺省为当天
     */
    public TimeSeriesLoader setEndTradingDay(LocalDate d){
        this.endTradingDay = d;
        return this;
    }

    /**
     * 设置最后一个交易日的最后的市场时间, 缺省为不限制
     */
    public TimeSeriesLoader setEndTime(LocalDateTime endTime){
        this.endTime = endTime;
        return this;
    }

    public List<LocalDate> getLoadedDates(){
        return Collections.unmodifiableList(loadedDates);
    }

    /**
     * 加载数据
     */
    public LeveledTimeSeries load() throws IOException {
        loadedDates.clear();
        if ( level==PriceLevel.DAY ) {
            return loadDaySeries();
        }
        if ( endTradingDay==null ) {
            endTradingDay = LocalDate.now();
        }

        LinkedList<Bar> bars = new LinkedList<>();
        LocalDate tradingDay = endTradingDay;

        //从后向前
        while(tradingDay.compareTo(startTradingDay)>=0) {
            List<Bar> dayBars = new ArrayList<>();
            if ( data.exists(exchangeable, ExchangeableData.MIN1, tradingDay) ) {
                dayBars = loadMin1Bars(tradingDay);
                if ( level!=PriceLevel.MIN1 ) {
                    dayBars = mergeMin1Bars(dayBars);
                }
            } else {
                dayBars = loadMinFromTicks(tradingDay);
            }
            //最后一个交易日可以没有数据...因为有可能是某天晚上加载数据.
            if ( dayBars.isEmpty() && !tradingDay.equals(endTradingDay) ) {
                break;
            }
            if ( !dayBars.isEmpty() ) {
                bars.addAll(0, dayBars);
                loadedDates.add(tradingDay);
            }
            //前一个交易日
            tradingDay = MarketDayUtil.prevMarketDay(exchangeable.exchange(), tradingDay);
        }
        //转换Bar为TimeSeries
        BaseLeveledTimeSeries result = new BaseLeveledTimeSeries(exchangeable.name()+"-"+level, level, LongNum::valueOf);
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
        int colIndex = csvDataSet.getColumnIndex(ExchangeableData.COLUMN_INDEX);
        while(csvDataSet.next()) {
            LocalDateTime beginTime = csvDataSet.getDateTime(ExchangeableData.COLUMN_BEGIN_TIME);
            LocalDateTime endTime = csvDataSet.getDateTime(ExchangeableData.COLUMN_END_TIME);
            if ( this.endTime!=null && this.endTime.isBefore(endTime)) {
                continue;
            }
            ZonedDateTime zonedBeginTime = beginTime.atZone(zoneId);
            ZonedDateTime zonedEndTime = endTime.atZone(zoneId);
            int barIndex=0;
            if ( colIndex>=0 ) {
                barIndex = csvDataSet.getInt(ExchangeableData.COLUMN_INDEX);
            } else {
                barIndex = getBarIndex(exchangeable, level, beginTime);
            }
            FutureBar bar = new FutureBar( barIndex,
                DateUtil.between(beginTime, endTime),
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
        List<MarketData> marketDatas = new ArrayList<>();
        if ( exchangeable.getType()==ExchangeableType.FUTURE ) {
            marketDatas = loadCtpTicks(actionDay);
        }
        return marketDatas2bars(exchangeable, level, marketDatas);
    }

    /**
     * 加载日线数据
     */
    private LeveledTimeSeries loadDaySeries() throws IOException
    {
        throw new IOException("日线数据未实现加载");
    }

    private List<MarketData> loadCtpTicks(LocalDate tradingDay) throws IOException
    {
        if ( !data.exists(exchangeable, ExchangeableData.TICK_CTP, tradingDay) ) {
            return Collections.emptyList();
        }
        List<MarketData> result = new ArrayList<>();
        CtpMarketDataProducer mdProducer = new CtpMarketDataProducer(null, null);
        CtpCSVMarshallHelper csvMarshallHelper = new CtpCSVMarshallHelper();
        String csv = data.load(exchangeable, ExchangeableData.TICK_CTP, tradingDay);
        CSVDataSet csvDataSet = CSVUtil.parse(csv);
        while(csvDataSet.next()) {
            MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), tradingDay);
            if ( this.endTime!=null && this.endTime.isBefore(marketData.updateTime)) {
                continue;
            }
            result.add(marketData);
        }
        return result;
    }

    /**
     * 将原始CTP TICK转为MIN1 Bar
     */
    public static List<Bar> marketDatas2bars(Exchangeable exchangeable, PriceLevel level ,List<MarketData> marketDatas){
        if ( marketDatas.isEmpty() ) {
            return Collections.emptyList();
        }
        List<Bar> result = new ArrayList<>();
        MarketData beginTick=marketDatas.get(0), lastTick=null;
        int lastTickIndex=getBarIndex(exchangeable, level, beginTick.updateTime);
        long high=beginTick.lastPrice, low=beginTick.lastPrice;
        for(int i=0;i<marketDatas.size();i++) {
            MarketData currTick = marketDatas.get(i);
            int currTickIndex = getBarIndex(exchangeable, level, currTick.updateTime);
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
                FutureBar bar = new FutureBar(currTickIndex, DateUtil.between(DateUtil.round(beginTick.updateTime), endTime),
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
            FutureBar bar = new FutureBar(lastTickIndex, DateUtil.between(beginTick.updateTime, lastTick.updateTime),
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
     * 根据时间返回当前KBar序列的位置
     * @return -1 如果未开市, 0-N
     */
    public static int getBarIndex(Exchangeable exchangeable, PriceLevel level, LocalDateTime marketTime)
    {
        if( level.ordinal()>=PriceLevel.DAY.ordinal() ){
            return 0;
        }
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
                //15:00:00.000 - 15:00:00.999
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
