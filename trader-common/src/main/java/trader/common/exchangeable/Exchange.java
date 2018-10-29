package trader.common.exchangeable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;

public class Exchange {

    /**
     * 市场类型: 日市夜市
     */
    public static enum MarketType{
        /**
         * 日盘
         */
        Day,
        /**
         * 夜盘
         */
        Night
    }

    private String name;

    /**
     * open-close
     * open-close
     */
    private LocalTime[] defaultDayMarketTimes;
    private LocalTime[] defaultNightMarketTimes;
    private ZoneId     zoneId;
    private ZoneOffset zoneOffset;
    private boolean    future;

    public String name() {
        return name;
    }

    /**
     * 期货交易所
     */
    public boolean isFuture() {
        return future;
    }

    /**
     * 政券交易所
     */
    public boolean isSecurity() {
        return !future;
    }

    public boolean hasMarket(MarketType marketType){
        switch(marketType){
        case Day:
            return defaultDayMarketTimes!=null;
        case Night:
            return defaultNightMarketTimes!=null;
        }
        return false;
    }

    private LocalTime[] getMarketTimes0(MarketType marketType, String instrumentId, LocalDate tradingDay){
        if ( marketType==MarketType.Night && defaultDayMarketTimes == null){
            throw new RuntimeException(name()+" 不支持夜盘");
        }
        ExchangeContract contract = ExchangeContract.matchContract(this, instrumentId);
        if ( contract== null ) {
            throw new RuntimeException("Unable to match exchange contract for "+this+"."+instrumentId);
        }

        for(ExchangeContract.TimeStage stage: contract.getTimeStages()) {
            if ( stage.getMarketType()==marketType ) {
                return stage.getTimeFrames();
            }
        }
        switch(marketType){
        case Day:
            return defaultDayMarketTimes;
        case Night:
            return defaultNightMarketTimes;
        }
        return null;
    }

    public LocalDateTime[] getMarketTimes(MarketType marketType, String instrumentId, LocalDate tradingDay)
    {
        LocalTime[] marketTimes = getMarketTimes0(marketType, instrumentId, tradingDay);
        if ( marketTimes==null ){
            return null;
        }
        switch(marketType){
        case Day:
            LocalDateTime[] result = new LocalDateTime[marketTimes.length];
            for(int i=0;i<marketTimes.length;i++){
                result[i] = tradingDay.atTime(marketTimes[i]);
            }
            return result;
        case Night:
            LocalTime beginTime = marketTimes[0];
            LocalTime endTime = marketTimes[marketTimes.length-1];
            LocalDate prevTradingDay = MarketDayUtil.prevMarketDay(this, tradingDay);
            LocalDateTime endDateTime = prevTradingDay.atTime(endTime);
            if ( endTime.getHour()<beginTime.getHour() ){ //next day
                endDateTime = prevTradingDay.plusDays(1).atTime(endTime);
            }
            return new LocalDateTime[]{ prevTradingDay.atTime(beginTime), endDateTime };
        }
        throw new RuntimeException("Should not run here.");
    }

    public LocalDateTime[] getOpenCloseTime(MarketType marketType, String instrumentId, LocalDate tradingDay)
    {
        LocalDateTime[] marketTimes = getMarketTimes(marketType, instrumentId, tradingDay);
        if ( marketTimes==null ){
            return null;
        }
        return new LocalDateTime[]{ marketTimes[0], marketTimes[marketTimes.length-1]};
    }

    public LocalTime[] getDefaultOpenCloseTime(MarketType marketType){
        switch(marketType){
        case Day:
            return new LocalTime[]{defaultDayMarketTimes[0], defaultDayMarketTimes[defaultDayMarketTimes.length-1]};
        case Night:
            if( defaultNightMarketTimes!=null ){
                return new LocalTime[]{defaultDayMarketTimes[0], defaultDayMarketTimes[defaultDayMarketTimes.length-1]};
            }
        }
        return null;
    }

    /**
     * 返回从交易开始算起毫秒数
     *
     * @return -1 不存在
     */
    public int getTradingMilliSeconds(MarketType marketType, String instrumentId, LocalDate tradingDay, LocalTime marketTime){
        LocalTime[] marketTimes = getMarketTimes0(marketType, instrumentId, tradingDay);
        if ( marketTimes==null ){
            return -1;
        }
        if ( marketTime.isBefore(marketTimes[0]) ){
            return 0;
        }
        int milliSeconds = 0;

        for(int i=0;i<marketTimes.length;i+=2){
            LocalTime beginTime = marketTimes[i];
            LocalTime endTime = marketTimes[i+1];
            if ( marketTime.isBefore(beginTime) ){ //In break time
                break;
            }
            if ( marketTime.isBefore(endTime) ){
                milliSeconds += ( marketTime.get(ChronoField.MILLI_OF_DAY) - beginTime.get(ChronoField.MILLI_OF_DAY));
            }else{
                milliSeconds += ( endTime.get(ChronoField.MILLI_OF_DAY) - beginTime.get(ChronoField.MILLI_OF_DAY));
            }
        }
        return milliSeconds;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public ZoneOffset getZoneOffset() {
        return zoneOffset;
    }

    private Exchange(String name, boolean future, LocalTime[] dayMarketTimes, LocalTime[] nightMarketTimes, ZoneId zoneId) {
        this.name = name;
        this.future = future;
        this.defaultDayMarketTimes = dayMarketTimes;
        this.defaultNightMarketTimes = nightMarketTimes;
        this.zoneId = zoneId;
        this.zoneOffset = LocalDateTime.now().atZone(zoneId).getOffset();
    }

    @Override
    public String toString() {
        return name;
    }

    private static final ZoneId ZONEID_BEIJING = ZoneId.of("Asia/Shanghai");
    private static LocalTime[] DAY_TIME_STOCK = new LocalTime[]{LocalTime.of(9, 30), LocalTime.of(11,30), LocalTime.of(13, 0), LocalTime.of(15, 0)};
    private static LocalTime[] DAY_TIME_CFFEX = new LocalTime[]{LocalTime.of(9, 15), LocalTime.of(11,30), LocalTime.of(13, 0), LocalTime.of(15, 15)};

    private static LocalTime[] DAY_TIME_FUTURE = new LocalTime[]{
            LocalTime.of(9, 0), LocalTime.of(10, 15),
            LocalTime.of(10, 30), LocalTime.of(11,30),
            LocalTime.of(13, 30), LocalTime.of(15, 0)};
    private static LocalTime[] NIGHT_TIME_FUTURE = new LocalTime[]{LocalTime.of(21, 00), LocalTime.of(23,00)};

    /**
     * 上证
     */
    public static final Exchange    SSE            = new Exchange("sse", false, DAY_TIME_STOCK, null, ZONEID_BEIJING);

    /**
     * 深证
     */
    public static final Exchange    SZSE           = new Exchange("szse", false, DAY_TIME_STOCK, null, ZONEID_BEIJING);

    /**
     * 港股
     */
    public static final Exchange    HKEX           = new Exchange("hkex", false, DAY_TIME_STOCK, null, ZONEID_BEIJING);

    /**
     * 中金所
     */
    public static final Exchange    CFFEX          = new Exchange("cffex", true, DAY_TIME_CFFEX, null, ZONEID_BEIJING);

    /**
     * 大连商品交易所
     */
    public static final Exchange    DCE            = new Exchange("dce", true, DAY_TIME_FUTURE, NIGHT_TIME_FUTURE, ZONEID_BEIJING);

    /**
     *  郑州商品交易所
     */
    public static final Exchange    CZCE            = new Exchange("czce", true, DAY_TIME_FUTURE, NIGHT_TIME_FUTURE, ZONEID_BEIJING);

    /**
     *  上海国际能源交易中心
     */
    public static final Exchange    INE            = new Exchange("ine", true, DAY_TIME_FUTURE, NIGHT_TIME_FUTURE, ZONEID_BEIJING);

    /**
     * 上期
     */
    public static final Exchange    SHFE           = new Exchange("shfe", true, DAY_TIME_FUTURE, NIGHT_TIME_FUTURE, ZONEID_BEIJING);

    public static final String      SSE_NAME       = SSE.name();
    public static final String      SZSE_NAME      = SZSE.name();
    public static final String      CFFEX_NAME     = CFFEX.name();
    public static final String      DCE_NAME       = DCE.name();
    public static final String      SHFE_NAME      = SHFE.name();

    private static final Exchange[] exchanges      = new Exchange[] { SSE, SZSE, CFFEX, DCE, SHFE, CZCE };

    public static Exchange getInstance(String exchangeName) {
        if (exchangeName == null || exchangeName.equalsIgnoreCase("SH")) {
            return SSE;
        }
        if (exchangeName.equalsIgnoreCase("sze") || exchangeName.equalsIgnoreCase("SZ")) {
            return SZSE;
        }
        for (Exchange e : exchanges) {
            if (exchangeName.equalsIgnoreCase(e.name())) {
                return e;
            }
        }
        return null;
    }

    public static final Exchange[] getInstances() {
        return exchanges;
    }

    public MarketType detectMarketTypeAt(Exchangeable e, LocalDateTime marketDateTime) {
        LocalDate marketDay = marketDateTime.toLocalDate();
        LocalTime marketTime = marketDateTime.toLocalTime();
        LocalTime[] dayTimes = getDefaultOpenCloseTime(MarketType.Day);
        dayTimes[0] = dayTimes[0].minusHours(1);
        dayTimes[1] = dayTimes[1].plusHours(1);
        if ( dayTimes[0].isBefore(marketTime) && marketTime.isBefore(dayTimes[1]) ){
            return MarketType.Day;
        }
        if ( !hasMarket(MarketType.Night) ){
            return null;
        }
        LocalDateTime[] nightTimes = null;
        if ( marketDateTime.getHour()< dayTimes[0].getHour() ){ //第二天凌晨了,交易日不变
            nightTimes = getOpenCloseTime(MarketType.Night, e.commodity(), marketDay);
        }else{ //同一天晚,交易日改为下一个交易日
            nightTimes = getOpenCloseTime(MarketType.Night, e.commodity(), MarketDayUtil.nextMarketDay(this, marketDay));
        }
        if ( nightTimes!=null ){
            nightTimes[0] = nightTimes[0].minusHours(1);
            nightTimes[1] = nightTimes[1].plusHours(1);
            if ( nightTimes[0].isBefore(marketDateTime) && marketDateTime.isBefore(nightTimes[1])){
                return MarketType.Night;
            }
        }
        return null;
    }

}
