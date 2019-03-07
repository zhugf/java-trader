package trader.service.ta;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.Num;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableData.DataInfo;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.exchangeable.MarketTimeStage;
import trader.common.tick.PriceLevel;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVMarshallHelper;
import trader.common.util.CSVUtil;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.md.MarketDataService;

/**
 * 行情数据加载和转换为分钟级别数据
 */
public class TimeSeriesLoader {

    private BeansContainer beansContainer;
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

    private Map<LocalDate, List<Bar>> min1BarsByDay = new HashMap<>();

    private List<LocalDate> loadedDates = new ArrayList<>();

    private Map<LocalDate, ExchangeableTradingTimes> tradingDays = new HashMap<>();

    public TimeSeriesLoader(BeansContainer beansContainer, ExchangeableData data) {
        this.beansContainer = beansContainer;
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
     * 直接加载行情切片原始数据
     */
    public List<MarketData> loadMarketDataTicks(LocalDate tradingDay, DataInfo tickDataInfo) throws IOException
    {
        if ( !data.exists(exchangeable, tickDataInfo, tradingDay) ) {
            return Collections.emptyList();
        }
        List<MarketData> result = new ArrayList<>();
        MarketDataService mdService = this.beansContainer.getBean(MarketDataService.class);
        MarketDataProducerFactory ctpFactory = mdService.getProducerFactories().get(tickDataInfo.provider());
        MarketDataProducer mdProducer = ctpFactory.create(beansContainer, null);
        CSVMarshallHelper csvMarshallHelper = ctpFactory.createCSVMarshallHelper();
        String csv = data.load(exchangeable, tickDataInfo, tradingDay);
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
            List<Bar> dayMinBars = new ArrayList<>();
            if ( min1BarsByDay.containsKey(tradingDay)) {
                dayMinBars = mergeMin1Bars(min1BarsByDay.get(tradingDay));
            } else if (data.exists(exchangeable, ExchangeableData.MIN1, tradingDay)) {
                List<Bar> dayMin1Bars = loadMin1Bars(tradingDay);
                min1BarsByDay.put(tradingDay, dayMin1Bars);
                dayMinBars = mergeMin1Bars(dayMin1Bars);
            } else {
                dayMinBars = loadMinFromTicks(tradingDay);
            }
            //最后一个交易日可以没有数据...因为有可能是某天晚上加载数据.
            if ( dayMinBars.isEmpty() && !tradingDay.equals(endTradingDay) ) {
                break;
            }
            if ( !dayMinBars.isEmpty() ) {
                bars.addAll(0, dayMinBars);
                loadedDates.add(tradingDay);
            }
            //前一个交易日
            tradingDay = MarketDayUtil.prevMarketDay(exchangeable.exchange(), tradingDay);
        }
        //转换Bar为TimeSeries
        BaseLeveledTimeSeries result = new BaseLeveledTimeSeries(exchangeable.name()+"-"+level, level, LongNum::valueOf);
        for(int i=0;i<bars.size();i++) {
            Bar bar = bars.get(i);
            result.addBar(bar);
        }
        return result;
    }

    /**
     * 将1分钟K线合并为多分钟K线
     */
    private List<Bar> mergeMin1Bars(List<Bar> min1Bars) {
        if ( level==PriceLevel.MIN1 ) {
            return min1Bars;
        }
        List<Bar> result = new ArrayList<>();

        int minutes = level.getValue();
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
        ExchangeableTradingTimes tradingTimes = exchangeable.exchange().getTradingTimes(exchangeable, actionDay);
        while(csvDataSet.next()) {
            LocalDateTime beginTime = csvDataSet.getDateTime(ExchangeableData.COLUMN_BEGIN_TIME);
            LocalDateTime endTime = csvDataSet.getDateTime(ExchangeableData.COLUMN_END_TIME);
            if ( this.endTime!=null && this.endTime.isBefore(endTime)) {
                continue;
            }
            ZonedDateTime zonedBeginTime = beginTime.atZone(zoneId);
            ZonedDateTime zonedEndTime = endTime.atZone(zoneId);
            int barIndex=0;
            String barIndexStr = null;
            if ( colIndex>0 ) {
                barIndexStr = csvDataSet.get(colIndex);
            }
            if ( !StringUtil.isEmpty(barIndexStr) ) {
                barIndex = ConversionUtil.toInt(barIndexStr);
            } else {
                barIndex = getBarIndex(tradingTimes, level, beginTime);
            }
            FutureBar bar = new FutureBar( barIndex,
                DateUtil.between(beginTime, endTime),
                zonedEndTime,
                new LongNum(csvDataSet.getPrice(ExchangeableData.COLUMN_OPEN)),
                new LongNum(csvDataSet.getPrice(ExchangeableData.COLUMN_HIGH)),
                new LongNum(csvDataSet.getPrice(ExchangeableData.COLUMN_LOW)),
                new LongNum(csvDataSet.getPrice(ExchangeableData.COLUMN_CLOSE)),
                new LongNum(PriceUtil.price2long(csvDataSet.getInt(ExchangeableData.COLUMN_VOLUME))),
                new LongNum(csvDataSet.getPrice(ExchangeableData.COLUMN_TURNOVER)),
                new LongNum(PriceUtil.price2long(csvDataSet.getLong(ExchangeableData.COLUMN_OPENINT))));
            result.add(bar);
        }
        return result;
    }

    /**
     * 加载某日的TICK数据, 转换为MIN1数据
     */
    private List<Bar> loadMinFromTicks(LocalDate tradingDay) throws IOException {
        List<MarketData> marketDatas = new ArrayList<>();
        if ( exchangeable.getType()==ExchangeableType.FUTURE ) {
            marketDatas = loadMarketDataTicks(tradingDay, ExchangeableData.TICK_CTP);
        }
        List<Bar> minBars = marketDatas2bars(exchangeable, level, marketDatas);
        if (level==PriceLevel.MIN1) {
            min1BarsByDay.put(tradingDay, minBars);
        }
        return minBars;
    }

    /**
     * 加载日线数据
     */
    private LeveledTimeSeries loadDaySeries() throws IOException
    {
        throw new IOException("日线数据未实现加载");
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
        ExchangeableTradingTimes tradingDay = exchangeable.exchange().getTradingTimes(exchangeable, DateUtil.str2localdate(beginTick.tradingDay));
        int lastBarIndex = getBarIndex(tradingDay, level, beginTick.updateTime);
        long high=beginTick.lastPrice, low=beginTick.lastPrice;
        for(int i=0;i<marketDatas.size();i++) {
            MarketData currTick = marketDatas.get(i);
            LocalDate currDay = DateUtil.str2localdate(currTick.tradingDay);
            if ( !currDay.equals(tradingDay.getTradingDay()) ){
                tradingDay = exchangeable.exchange().getTradingTimes(exchangeable, currDay);
            }
            int currTickIndex = getBarIndex(tradingDay, level, currTick.updateTime);
            if ( currTickIndex<0 ) {
                continue;
            }
            if ( lastBarIndex!=currTickIndex ) {
                //创建新的Bar
                LocalDateTime[] barTimes = getBarTimes(tradingDay, level, lastBarIndex, beginTick.updateTime);
                MarketData endTick = lastTick;
                if ( currTickIndex>lastBarIndex ) { //今天的连续Bar
                    if ( currTick.updateTime.equals(barTimes[1]) ) {
                        endTick = currTick;
                        high = Math.max(high, endTick.lastPrice);
                        low = Math.min(low, endTick.lastPrice);
                    }
                }
                FutureBar bar = new FutureBar(lastBarIndex, DateUtil.between(barTimes[0], barTimes[1]),
                barTimes[1].atZone(exchangeable.exchange().getZoneId()),
                                new LongNum(beginTick.lastPrice),
                                new LongNum(high),
                                new LongNum(low),
                                new LongNum(endTick.lastPrice),
                                new LongNum(PriceUtil.price2long(endTick.volume-beginTick.volume)),
                                new LongNum(endTick.turnover-beginTick.turnover),
                                new LongNum(PriceUtil.price2long(endTick.openInterest))
                                );
                result.add(bar);

                if( lastBarIndex>currTickIndex ) { //换了日市夜市
                    beginTick = currTick;
                }else {
                    beginTick = endTick;
                }
                high = low = currTick.lastPrice;
                lastBarIndex=currTickIndex;
                continue;
            }
            high = Math.max(high, currTick.lastPrice);
            low = Math.min(low, currTick.lastPrice);
            lastTick = currTick;
        }
        //Convert market data to MIN1
        lastTick = marketDatas.get(marketDatas.size()-1);
        if ( lastTick!=beginTick ) {
            LocalDateTime[] barTimes = getBarTimes(tradingDay, level, -1, beginTick.updateTime);
            FutureBar bar = new FutureBar(lastBarIndex, DateUtil.between(barTimes[0], lastTick.updateTime),
                    barTimes[1].atZone(exchangeable.exchange().getZoneId()),
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
     * 返回KBar的起始和结束时间
     */
    public static LocalDateTime[] getBarTimes(ExchangeableTradingTimes tradingTimes, PriceLevel level, int barIndex, LocalDateTime barBeginTime)
    {
        if ( barIndex<0 ) {
            barIndex = getBarIndex(tradingTimes, level, barBeginTime);
        }
        LocalDateTime marketCloseTime = tradingTimes.getMarketCloseTime();
        LocalDateTime beginTime = tradingTimes.getMarketTimes()[0], endTime = null;
        while(beginTime.compareTo(marketCloseTime)<=0) {
            if ( getBarIndex(tradingTimes, level, beginTime)==barIndex && tradingTimes.getTimeStage(beginTime)==MarketTimeStage.MarketOpen) {
                break;
            }
            beginTime = beginTime.plusMinutes(1);
        }
        endTime = beginTime.plusMinutes(level.getValue());
        LocalDateTime[] result = new LocalDateTime[] {beginTime, endTime};
        return result;
    }

    /**
     * 根据时间返回当前KBar序列的位置
     * @return -1 如果未开市, 0-N
     */
    public static int getBarIndex(ExchangeableTradingTimes tradingTimes, PriceLevel level, LocalDateTime marketTime)
    {
        if( level==PriceLevel.DAY ){
            return 0;
        }
        if ( tradingTimes==null ) {
            return -1;
        }
        MarketTimeStage mts = tradingTimes.getTimeStage(marketTime);
        switch(mts){
        case AggregateAuction:
        case BeforeMarketOpen:
            return -1;
        case MarketOpen:
        case MarketBreak:
            break;
        default:
            return -1;
        }

        int tradingMillis = tradingTimes.getTradingTime(marketTime);
        int tradeMinutes = tradingMillis / (1000*60);
        int tickIndex=0;

        LocalDateTime[] marketTimes = tradingTimes.getMarketTimes();
        for(int i=0;i<marketTimes.length;i+=2) {
            LocalDateTime stageBegin = marketTimes[i];
            LocalDateTime stageEnd = marketTimes[i+1];
            LocalDateTime stageBegin2 = null;
            if ( i<marketTimes.length-2) {
                stageBegin2 = marketTimes[i+2];
            }
            //如果已经是下一个时间段, 直接跳过当前时间段
            if ( stageBegin2!=null && marketTime.compareTo(stageBegin2)>=0 ) {
                continue;
            }
            if ( marketTime.compareTo(stageBegin)<0 ) {
                break;
            }
            int compareResult = marketTime.compareTo(stageEnd);
            tickIndex = tradeMinutes/level.getValue();
            int tickBeginMillis = tickIndex*level.getValue()*60*1000;
            if ( compareResult>=0 ) {
                //超过当前时间段, 但是没有到下一个时间段, 算在最后一个KBar
                if ( tradingMillis-tickBeginMillis<5*1000 ) {
                    tickIndex -= 1;
                }
            }
            break;
        }

        return tickIndex;
    }

    ExchangeableTradingTimes getTradingTimes(LocalDate day) {
        ExchangeableTradingTimes result = tradingDays.get(day);
        if (result==null) {
            result = exchangeable.exchange().getTradingTimes(exchangeable, day);
            tradingDays.put(day, result);
        }
        return result;
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
