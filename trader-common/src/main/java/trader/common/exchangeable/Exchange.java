package trader.common.exchangeable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import trader.common.exchangeable.ExchangeContract.MarketTimeRecord;
import trader.common.exchangeable.ExchangeContract.MarketTimeSegment;
import trader.common.exchangeable.ExchangeableTradingTimes.MarketTimeSegmentInfo;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;

public class Exchange {

    private String name;
    private ZoneId zoneId;
    private ZoneOffset zoneOffset;
    private boolean future;
    private Map<String, ExchangeContract> contracts;
    private LocalTime[] marketTimes;

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

    public ZoneId getZoneId() {
        return zoneId;
    }

    public ZoneOffset getZoneOffset() {
        return zoneOffset;
    }

    public LocalTime[] getMarketTimes() {
        return marketTimes;
    }

    /**
     * 规范交易所品种大小写
     */
    public String canonicalCommodity(String commodity) {
        for(String key:contracts.keySet()) {
            if ( StringUtil.equalsIgnoreCase(key, commodity)) {
                return key;
            }
        }
        return commodity;
    }

    public ExchangeableTradingTimes detectTradingTimes(Exchangeable e, LocalDateTime time) {
        return detectTradingTimes(e.id(), time);
    }

    public ExchangeableTradingTimes getTradingTimes(Exchangeable instrument, LocalDate tradingDay) {
        return getTradingTimes(instrument.id(), tradingDay);
    }

    public ExchangeableTradingTimes detectTradingTimes(String instrumentId, LocalDateTime time) {
        ExchangeableTradingTimes result = null;

        int hhmmss = DateUtil.time2int(time.toLocalTime());
        LocalDate tradingDay = time.toLocalDate();
        if ( hhmmss<=30000 ) {
            //凌晨使用前一天的下一个TradingDay
            tradingDay =  MarketDayUtil.nextMarketDay(this, tradingDay.minusDays(1));
        }
        result = getTradingTimes(instrumentId, tradingDay);
        if ( result!=null ) {
            LocalDateTime[] marketTimes = result.getMarketTimes();
            if ( time.compareTo(marketTimes[marketTimes.length-1].plusHours(2))<=0 ){
                return result;
            }
            result = getTradingTimes(instrumentId, MarketDayUtil.nextMarketDay(this, time.toLocalDate()));
        }
        return result;
    }

    public ExchangeableTradingTimes getTradingTimes(String instrumentId, LocalDate tradingDay) {
        if ( !MarketDayUtil.isMarketDay(this, tradingDay)) {
            return null;
        }
        ExchangeContract contract = matchContract(instrumentId);
        if( contract==null ) {
            return null;
        }
        LinkedList<LocalDateTime> marketTimes = new LinkedList<>();
        List<MarketTimeSegmentInfo> segmentInfos = new ArrayList<>();
        MarketTimeRecord timeRecord = contract.matchMarketTimeRecords(tradingDay);
        for(MarketTimeSegment segment:timeRecord.getTimeStages()) {
            LocalDate stageTradingDay = tradingDay;
            if ( segment.lastTradingDay ) {
                stageTradingDay = MarketDayUtil.prevMarketDay(this, tradingDay, true);
            }
            if ( stageTradingDay!=null ) {
                List<LocalDateTime> segTimes = new ArrayList<>();
                for(int i=0;i<segment.timeFrames.length;i++) {
                    LocalDateTime time = segment.timeFrames[i].atDate(stageTradingDay);
                    if ( i>0 && time.isBefore(marketTimes.getLast()) ){
                        time = time.plusDays(1);
                    }
                    marketTimes.add(time);
                    segTimes.add(time);
                }
                segmentInfos.add(new MarketTimeSegmentInfo(segment, segTimes.toArray(new LocalDateTime[segTimes.size()])));
            }
        }

        return new ExchangeableTradingTimes(Exchangeable.fromString(name(), instrumentId), tradingDay
                ,marketTimes.toArray(new LocalDateTime[marketTimes.size()])
                , segmentInfos );
    }

    public ExchangeContract matchContract(String instrument) {
        //证券交易所, 找 sse.* 这种
        if ( isSecurity() ) {
            return contracts.get("*");
        }
        //期货交易所, 找 cffex.TF1810, 找cffex.TF, 再找 cffex.*
        ExchangeContract contract = contracts.get(instrument);
        if ( contract==null ) {
            StringBuilder commodity = new StringBuilder(10);
            for(int i=0;i<instrument.length();i++) {
                char ch = instrument.charAt(i);
                if ( ch>='0' && ch<='9' ) {
                    break;
                }
                commodity.append(ch);
            }
            contract = contracts.get(commodity.toString().toUpperCase());
            if ( contract==null ) {
                contract = contracts.get(commodity.toString().toLowerCase());
            }
        }
        if ( contract==null ) {
            contract = contracts.get("*");
        }
        return contract;
    }

    /**
     * 返回交易所的合约列表
     */
    public Collection<String> getContractNames(){
        TreeSet<String> result = new TreeSet<>();
        for(String key:contracts.keySet()) {
            if ( key.equals("*") ) {
                continue;
            }
            result.add(key);
        }
        return result;
    }

    private Exchange(String name, boolean future, ZoneId zoneId, LocalTime[] marketTimes) {
        this.name = name;
        this.future = future;
        this.zoneId = zoneId;
        this.zoneOffset = LocalDateTime.now().atZone(zoneId).getOffset();
        this.marketTimes = marketTimes;
        contracts = new HashMap<>();
        for(ExchangeContract contract: ExchangeContract.getContracts(name)) {
            for(String commodity:contract.getCommodities()) {
                contracts.put(commodity, contract);
            }
        }
    }

    @Override
    public String toString() {
        return name;
    }

    private static final ZoneId ZONEID_BEIJING = ZoneId.of("Asia/Shanghai");
    private static LocalTime[] DAY_TIME_STOCK = new LocalTime[]{LocalTime.of(9, 30), LocalTime.of(15, 0)};
    private static LocalTime[] DAY_TIME_CFFEX = new LocalTime[]{LocalTime.of(9, 15), LocalTime.of(15, 15)};

    private static LocalTime[] DAY_TIME_FUTURE = new LocalTime[]{ LocalTime.of(9, 0), LocalTime.of(15, 0)};

    /**
     * 上证
     */
    public static final Exchange    SSE            = new Exchange("sse", false, ZONEID_BEIJING, DAY_TIME_STOCK);

    /**
     * 深证
     */
    public static final Exchange    SZSE           = new Exchange("szse", false, ZONEID_BEIJING, DAY_TIME_STOCK);

    /**
     * 港股
     */
    public static final Exchange    HKEX           = new Exchange("hkex", false, ZONEID_BEIJING, DAY_TIME_STOCK);

    /**
     * 中金所
     */
    public static final Exchange    CFFEX          = new Exchange("cffex", true, ZONEID_BEIJING, DAY_TIME_CFFEX);

    /**
     * 大连商品交易所
     */
    public static final Exchange    DCE            = new Exchange("dce", true, ZONEID_BEIJING, DAY_TIME_FUTURE);

    /**
     *  郑州商品交易所, 匪所
     */
    public static final Exchange    CZCE            = new Exchange("czce", true, ZONEID_BEIJING, DAY_TIME_FUTURE);

    /**
     *  上海国际能源交易中心
     */
    public static final Exchange    INE            = new Exchange("ine", true, ZONEID_BEIJING, DAY_TIME_FUTURE);

    /**
     * 上期
     */
    public static final Exchange    SHFE           = new Exchange("shfe", true, ZONEID_BEIJING, DAY_TIME_FUTURE);

    public static final String      SSE_NAME       = SSE.name();
    public static final String      SZSE_NAME      = SZSE.name();
    public static final String      CFFEX_NAME     = CFFEX.name();
    public static final String      DCE_NAME       = DCE.name();
    public static final String      SHFE_NAME      = SHFE.name();

    private static final Exchange[] exchanges      = new Exchange[] { SSE, SZSE, CFFEX, DCE, SHFE, CZCE, INE };

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

}
