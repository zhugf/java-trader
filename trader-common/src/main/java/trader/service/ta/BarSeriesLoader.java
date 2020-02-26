package trader.service.ta;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.ta4j.core.Bar;

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
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.md.MarketDataService;

/**
 * 行情数据加载和转换为分钟级别数据
 */
public class BarSeriesLoader {

    private BeansContainer beansContainer;
    private ExchangeableData data;
    private Exchangeable instrument;
    private PriceLevel level;
    /**
     * 对于VOLDaily这种需要动态解决Level的值, 保存Resolve之后的结果
     */
    private PriceLevel resolvedLevel;
    /**
     * 每天的KBar数量为 volume/(openInt/multiplier)
     */
    private int volDaliyMultiplier = 500;
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

    private Map<LocalDate, List<FutureBar>> min1BarsByDay = new HashMap<>();

    private List<LocalDate> loadedDates = new ArrayList<>();

    private Map<LocalDate, ExchangeableTradingTimes> tradingDays = new HashMap<>();

    public BarSeriesLoader(BeansContainer beansContainer, ExchangeableData data) {
        this.beansContainer = beansContainer;
        this.data = data;
    }

    public BeansContainer getBeansContainer() {
        return beansContainer;
    }

    public ExchangeableData getData() {
        return data;
    }

    public BarSeriesLoader setInstrument(Exchangeable e) {
        this.instrument = e;
        return this;
    }

    /**
     * 设置目标级别
     */
    public BarSeriesLoader setLevel(PriceLevel level) {
        this.level = level;
        return this;
    }

    /**
     * 设置第一个交易日, 缺省为当天
     */
    public BarSeriesLoader setStartTradingDay(LocalDate d){
        this.startTradingDay = d;
        return this;
    }

    /**
     * 设置最后一个交易日, 缺省为当天
     */
    public BarSeriesLoader setEndTradingDay(LocalDate d){
        this.endTradingDay = d;
        return this;
    }

    /**
     * 设置最后一个交易日的最后的市场时间, 缺省为不限制
     */
    public BarSeriesLoader setEndTime(LocalDateTime endTime){
        this.endTime = endTime;
        return this;
    }

    public List<LocalDate> getLoadedDates(){
        return Collections.unmodifiableList(loadedDates);
    }

    public void setVolDailyMultiplier(int multiplier) {
        this.volDaliyMultiplier = multiplier;
    }

    /**
     * 直接加载行情切片原始数据
     */
    public List<MarketData> loadMarketDataTicks(LocalDate tradingDay, DataInfo tickDataInfo) throws IOException
    {
        if ( !data.exists(instrument, tickDataInfo, tradingDay) ) {
            return Collections.emptyList();
        }
        List<MarketData> result = new ArrayList<>();
        MarketDataService mdService = this.beansContainer.getBean(MarketDataService.class);
        MarketDataProducerFactory ctpFactory = mdService.getProducerFactories().get(tickDataInfo.provider());
        MarketDataProducer mdProducer = ctpFactory.create(beansContainer, null);
        CSVMarshallHelper csvMarshallHelper = ctpFactory.createCSVMarshallHelper();
        String csv = data.load(instrument, tickDataInfo, tradingDay);
        CSVDataSet csvDataSet = CSVUtil.parse(csv);
        ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, tradingDay);

        //修在updateTime/updateTimstamp数据, 对于匪所, 同一秒的TICK序言耗时增加200MS
        long lastTimestamp = 0;
        while(csvDataSet.next()) {
            MarketData tick = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), tradingDay);
            if ( this.endTime!=null && this.endTime.isBefore(tick.updateTime)) {
                continue;
            }
            if ( lastTimestamp>=tick.updateTimestamp ) {
                tick.updateTimestamp = lastTimestamp+200;
                tick.updateTime = Instant.ofEpochMilli(tick.updateTimestamp).atZone(tick.instrument.exchange().getZoneId()).toLocalDateTime();
            }
            tick.postProcess(tradingTimes);
            lastTimestamp = tick.updateTimestamp;
            result.add(tick);
        }
        return result;
    }

    /**
     * 加载数据
     */
    public LeveledBarSeries load() throws IOException {
        if ( endTradingDay==null ) {
            endTradingDay = LocalDate.now();
        }
        loadedDates.clear();
        if ( level==PriceLevel.DAY ) {
            return loadDaySeries();
        }
        LinkedList<FutureBar> bars = new LinkedList<>();
        resolvedLevel = level;

        if ( level.name().startsWith(PriceLevel.LEVEL_MIN)) { //基于时间切分BAR
            LocalDate tradingDay = endTradingDay;
            //从后向前
            while(tradingDay.compareTo(startTradingDay)>=0) {
                if (data.exists(instrument, ExchangeableData.MIN1, tradingDay)) {
                    List<FutureBar> dayMin1Bars = loadMin1Bars(tradingDay);
                    min1BarsByDay.put(tradingDay, dayMin1Bars);
                }
                List<FutureBar> dayBars = new ArrayList<>();
                if ( min1BarsByDay.containsKey(tradingDay)) {
                    dayBars = timedBarsFromMin1(tradingDay, min1BarsByDay.get(tradingDay));
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
                tradingDay = MarketDayUtil.prevMarketDay(instrument.exchange(), tradingDay);
            }
        }else if ( level.name().startsWith(PriceLevel.LEVEL_VOL)) { //基于交易量切分BAR
            LocalDate tradingDay = startTradingDay;
            //从后向前
            while(tradingDay.compareTo(endTradingDay)<=0) {
                bars.addAll(loadVolBars(tradingDay, level));
                tradingDay = MarketDayUtil.nextMarketDay(instrument.exchange(), tradingDay);
            }
        }
        //转换Bar为TimeSeries
        BaseLeveledBarSeries result = new BaseLeveledBarSeries(instrument, instrument.name()+"-"+resolvedLevel, resolvedLevel, LongNum::valueOf);
        for(int i=0;i<bars.size();i++) {
            Bar bar = bars.get(i);
            result.addBar(bar);
        }
        return result;
    }

    /**
     * 将1分钟K线合并为多分钟K线
     */
    private List<FutureBar> timedBarsFromMin1(LocalDate tradingDay, List<FutureBar> min1Bars) {
        if ( level==PriceLevel.MIN1 ) {
            return min1Bars;
        }
        List<FutureBar> result = new ArrayList<>();

        ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, tradingDay);
        List<FutureBar> levelBars = new ArrayList<>();
        int lastBarIndex = -1;
        for(FutureBar min1Bar:min1Bars) {
            int barIndex = getBarIndex(tradingTimes, level, min1Bar.getBeginTime().toLocalDateTime());
            if ( barIndex!=lastBarIndex && levelBars.size()>0 ) {
                result.add(timedBarFromMin1(tradingTimes, lastBarIndex, levelBars));
                levelBars.clear();
            }
            levelBars.add(min1Bar);
            lastBarIndex = barIndex;
        }
        if ( levelBars.size()>0 ) {
            result.add(timedBarFromMin1(tradingTimes, lastBarIndex, levelBars));
            levelBars.clear();
        }
        return result;
    }

    /**
     * 合并MIN1为目标级别Bar
     */
    private FutureBar timedBarFromMin1(ExchangeableTradingTimes tradingTimes, int barIndex, List<FutureBar> bars) {
        FutureBar result = FutureBar.fromBars(barIndex, tradingTimes, bars);
        return result;
    }

    /**
     * 加载某日的MIN1数据
     */
    private List<FutureBar> loadMin1Bars(LocalDate tradingDay) throws IOException {
        List<FutureBar> result = new ArrayList<>();
        ZoneId zoneId = instrument.exchange().getZoneId();
        String csv = data.load(instrument, ExchangeableData.MIN1, tradingDay);
        CSVDataSet csvDataSet = CSVUtil.parse(csv);
        int colIndex = csvDataSet.getColumnIndex(ExchangeableData.COLUMN_INDEX);
        ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, tradingDay);
        while(csvDataSet.next()) {
            LocalDateTime beginTime = csvDataSet.getDateTime(ExchangeableData.COLUMN_BEGIN_TIME);
            LocalDateTime endTime = csvDataSet.getDateTime(ExchangeableData.COLUMN_END_TIME);
            if ( this.endTime!=null && this.endTime.isBefore(endTime)) {
                continue;
            }
            FutureBar bar = FutureBar.fromCSV(csvDataSet, instrument, tradingTimes);
            result.add(bar);
        }
        return result;
    }

    /**
     * 加载某日的TICK数据, 转换为MIN1数据
     */
    private List<FutureBar> loadMinFromTicks(LocalDate tradingDay) throws IOException {
        List<MarketData> marketDatas = loadMarketData(tradingDay);
        List<FutureBar> minBars = marketDatas2bars(instrument, tradingDay, level, marketDatas);
        if (level==PriceLevel.MIN1) {
            min1BarsByDay.put(tradingDay, minBars);
        }
        return minBars;
    }

    /**
     * 将TICK数据转换为 VOL10K Bar这种数据, 如果TICK之间的volume不能被整除, 不会再次切分TICK.因为这是最小单位.
     */
    private Collection<FutureBar> loadVolBars(LocalDate tradingDay, PriceLevel level) throws IOException
    {
        resolvedLevel = level;
        boolean resolveVolDaily = false;
        if ( level.value()<0 ) {
            resolveVolDaily=true;
        }
        List<FutureBar> result = new ArrayList<>();
        List<MarketData> marketDatas = loadMarketData(tradingDay);
        int currIndex =0;
        FutureBar currBar = null;
        ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, tradingDay);
        for(int i=0;i<marketDatas.size();i++) {
            MarketData md = marketDatas.get(i);
            if ( tradingTimes.getTimeStage(md.updateTime)!=MarketTimeStage.MarketOpen ) {
                continue;
            }
            if ( resolveVolDaily ) { //如果有必要, 每天动态修正volDaily为实际的值
                int volLevel = (int)md.openInterest/volDaliyMultiplier;
                level = PriceLevel.valueOf(PriceLevel.LEVEL_VOL+volLevel);
                resolveVolDaily = false;
                resolvedLevel = level;
            }
            if ( currBar!=null && currBar.getVolume().doubleValue()<level.value() ) {
                currBar.update(md, md.updateTime);
                continue;
            }
            MarketData mdBegin = md;
            if (i>0) {
                mdBegin = marketDatas.get(i-1);
            }
            currBar = FutureBar.fromTicks(currIndex++, tradingTimes, DateUtil.round(mdBegin.updateTime), mdBegin, md, md.lastPrice, md.lastPrice);
            result.add(currBar);
        }
        return result;
    }

    private List<MarketData> loadMarketData(LocalDate tradingDay) throws IOException {
        List<MarketData> marketDatas = new ArrayList<>();
        if ( instrument.getType()==ExchangeableType.FUTURE ) {
            marketDatas = loadMarketDataTicks(tradingDay, ExchangeableData.TICK_CTP);
        }
        return marketDatas;
    }

    /**
     * 加载日线数据
     */
    private LeveledBarSeries loadDaySeries() throws IOException
    {
        BaseLeveledBarSeries result = new BaseLeveledBarSeries(instrument, instrument.name()+"-"+resolvedLevel, resolvedLevel, LongNum::valueOf);
        if ( !data.exists(instrument, ExchangeableData.DAY, null)) {
            return result;
        }
        String csv = data.load(instrument, ExchangeableData.DAY, null);
        CSVDataSet csvDataSet = CSVUtil.parse(csv);
        int colIndex = csvDataSet.getColumnIndex(ExchangeableData.COLUMN_INDEX);
        while(csvDataSet.next()) {
            LocalDate date = csvDataSet.getDate(ExchangeableData.COLUMN_DATE);
            if ( startTradingDay!=null && date.isBefore(startTradingDay)) {
                continue;
            }
            //不包含当天
            if ( endTradingDay!=null && date.compareTo(endTradingDay)>=0 ) {
                continue;
            }
            FutureBar bar = FutureBar.fromDayCSV(csvDataSet, instrument);
            result.addBar(bar);
        }
        return result;
    }

    /**
     * 将原始CTP TICK转为MIN1 Bar
     */
    public static List<FutureBar> marketDatas2bars(Exchangeable exchangeable, LocalDate tradingDay, PriceLevel level ,List<MarketData> ticks){
        if ( ticks.isEmpty() ) {
            return Collections.emptyList();
        }
        ExchangeableTradingTimes tradingTimes = exchangeable.exchange().getTradingTimes(exchangeable, tradingDay);
        List<FutureBar> result = new ArrayList<>();
        int barIndex = 0;
        List<MarketData> barTicks = new ArrayList<>();
        for(int i=0;i<ticks.size();i++) {
            MarketData currTick = ticks.get(i);
            if ( tradingTimes.getTimeStage(currTick.updateTime)!=MarketTimeStage.MarketOpen ) {
                continue;
            }
            int currTickIndex = getBarIndex(tradingTimes, level, currTick.updateTime);
            if ( currTickIndex<0 ) {
                continue;
            }
            if ( currTickIndex!=barIndex ) {
                if ( barTicks.size()>0 ) {
                    LocalDateTime[] barTimes = getBarTimes(tradingTimes, level, barIndex, barTicks.get(0).updateTime);
                    if ( currTick.updateTime.equals(barTimes[1])) {
                        barTicks.add(currTick);
                    }
                    //创建新的Bar
                    result.add( createBarFromTicks(tradingTimes, barTimes, barTicks, barIndex) );
                }
                barTicks.clear();
                barIndex = currTickIndex;
            }
            barTicks.add(currTick);
        }
        if ( barTicks.size()>0 ) {
            LocalDateTime[] barTimes = getBarTimes(tradingTimes, level, barIndex, barTicks.get(0).updateTime);
            result.add( createBarFromTicks(tradingTimes, barTimes, barTicks, barIndex) );
        }
        return result;
    }

    private static FutureBar createBarFromTicks(ExchangeableTradingTimes tradingTimes, LocalDateTime[] barTimes, List<MarketData> barTicks, int barIndex) {
        MarketData beginTick = barTicks.get(0);
        MarketData endTick = barTicks.get(barTicks.size()-1);
        long high = beginTick.lastPrice, low = beginTick.lastPrice;

        MarketData lastTick=beginTick, tick = null;
        for(int i=1;i<barTicks.size();i++) {
            tick = barTicks.get(i);
            if ( lastTick.highestPrice!=tick.highestPrice && PriceUtil.isValidPrice(tick.highestPrice) ) {
                high = tick.highestPrice;
            }
            if ( high<tick.lastPrice ) {
                high = tick.lastPrice;
            }

            if ( lastTick.lowestPrice!=tick.lowestPrice && PriceUtil.isValidPrice(tick.lowestPrice) ) {
                low = tick.lowestPrice;
            }
            if ( low>tick.lastPrice) {
                low = tick.lastPrice;
            }
        }
        FutureBar bar = FutureBar.fromTicks(barIndex, tradingTimes, barTimes[0], beginTick, endTick, high, low);
        bar.updateEndTime(barTimes[1].atZone(tradingTimes.getInstrument().exchange().getZoneId()));
        return bar;
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
        endTime = beginTime.plusMinutes(level.value());
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
            tickIndex = tradeMinutes/level.value();
            int tickBeginMillis = tickIndex*level.value()*60*1000;
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
            result = instrument.exchange().getTradingTimes(instrument, day);
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
