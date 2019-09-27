package trader.common.exchangeable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.IOUtil;
import trader.common.util.StringUtil;

/**
 * 交易所合约信息
 */
public class ExchangeContract {

    /**
     * 代表一个日市/夜市时间段. 之前5分钟有集合竞价
     */
    public static class MarketTimeSegment{
        public boolean lastTradingDay;
        public LocalTime[] timeFrames;
        public MarketType marketType;
    }

    /**
     * 代表一个交易时间定义, 同一个品种. 会存在多个MarketTimeRecord. 用于描述交易所调整交易时间规定的修订记录,
     */
    public static class MarketTimeRecord {
        /**
         * 有效期-开始日期
         */
        LocalDate beginDate;
        /**
         * 有效期-结束日期
         */
        LocalDate endDate;

        /**
         * 日市夜市的交易时间
         */
        MarketTimeSegment[] timeStages;

        public MarketTimeSegment[] getTimeStages() {
            return timeStages;
        }
        public LocalDate getBeginDate() {
            return beginDate;
        }
        public LocalDate getEndDate() {
            return endDate;
        }
    }

    private String[] commodities;

    private String[] instruments;

    private String instrumentFormat = "YYMM";

    private DayOfWeek lastTradingDayOfWeek;

    private int lastTradingDayOfMonth;

    private int lastTradingWeekOfMonth;

    /**
     * 交易时间段
     */
    private MarketTimeRecord[] marketTimeRecords;

    private double priceTick = 0.01;

    private int volumeMultiplier = 1;

    public double getPriceTick() {
        return priceTick;
    }

    public int getVolumeMultiplier() {
        return volumeMultiplier;
    }

    public MarketTimeRecord[] getMarketTimeRecords() {
        return marketTimeRecords;
    }

    /**
     * 合约名
     */
    public String[] getCommodities() {
        return commodities;
    }

    /**
     * 合约的实例时间标志:
     * ThisQuarter, NextQuarter, NextQuarter2, Next12Months
     */
    public String[] getInstruments() {
        return instruments;
    }

    public String getInstrumentFormat() {
        return instrumentFormat;
    }

    /**
     * 交易时间段
     */
    public MarketTimeRecord matchMarketTimeRecords(LocalDate tradingDay) {
        for(int i=0;i<marketTimeRecords.length;i++) {
            MarketTimeRecord record = marketTimeRecords[i];
            if ( record.beginDate.compareTo(tradingDay)<=0 && record.endDate.compareTo(tradingDay)>=0 ) {
                return marketTimeRecords[i];
            }
        }
        return null;
    }

    public DayOfWeek getLastTradingDayOfWeek() {
        return lastTradingDayOfWeek;
    }

    public int getLastTradingDayOfMonth() {
        return lastTradingDayOfMonth;
    }

    public int getLastTradingWeekOfMonth() {
        return lastTradingWeekOfMonth;
    }

    public boolean isAfterLastTradingDay(LocalDate tradingDay) {
        boolean result = false;
        if ( getLastTradingDayOfMonth()>0 && tradingDay.getDayOfMonth()>=getLastTradingDayOfMonth() ) {
            result = true;
        }
        DayOfWeek dayOfWeek = tradingDay.getDayOfWeek();
        int weekOfMonth = tradingDay.get(WeekFields.of(DayOfWeek.SUNDAY, 2).weekOfMonth());
        if (getLastTradingWeekOfMonth() > 0 &&
                (weekOfMonth > getLastTradingWeekOfMonth()
                        || (weekOfMonth == getLastTradingWeekOfMonth() && dayOfWeek.getValue() > getLastTradingDayOfWeek().getValue()))
           )
        {
            result = true;
        }
        return result;
    }


    public static List<ExchangeContract> getContracts(String exchange) {
        if ( StringUtil.isEmpty(exchange)) {
            return new ArrayList<>(contracts.values());
        }
        List<ExchangeContract> result = new ArrayList<>();
        for(String key:contracts.keySet()) {
            if ( key.toUpperCase().endsWith("."+exchange.toUpperCase())) {
                result.add(contracts.get(key));
            }
        }
        return result;
    }

    public static Exchange detectContract(String commodity) {
        for(String key:contracts.keySet()) {
            if ( key.toUpperCase().startsWith(commodity.toUpperCase()+".")) {
                String exchangeName = key.substring(commodity.length()+1);
                return Exchange.getInstance(exchangeName);
            }
        }
        return null;
    }

    private static final Map<String, ExchangeContract> contracts = new HashMap<>();

    static {
        try{
            loadContracts();
        }catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void loadContracts() throws Exception
    {
        JsonArray jsonArray = (JsonArray)(new JsonParser()).parse( IOUtil.readAsString(ExchangeContract.class.getResourceAsStream("exchangeContracts.json")) );
        for(int i=0;i<jsonArray.size();i++) {
            JsonObject json = (JsonObject)jsonArray.get(i);
            String exchange = json.get("exchange").getAsString();
            String commodities[] = json2stringArray( (JsonArray)json.get("commodity") );
            ExchangeContract contract = new ExchangeContract();
            contract.commodities = commodities;
            if ( json.has("instruments") ) {
                contract.instruments = json2stringArray((JsonArray)json.get("instruments"));
            }
            if ( json.has("instrumentFormat")) {
                contract.instrumentFormat = json.get("instrumentFormat").getAsString();
            }
            if ( json.has("priceTick")) {
                contract.priceTick = json.get("priceTick").getAsDouble();
            }
            if ( json.has("volumeMultiplier")) {
                contract.volumeMultiplier = (int)json.get("volumeMultiplier").getAsDouble();
            }
            if ( json.has("lastTradingDay")) {

                String lastTradingDay = json.get("lastTradingDay").getAsString();
                if ( lastTradingDay.indexOf(".")>0){
                    contract.lastTradingWeekOfMonth = ConversionUtil.toInt( lastTradingDay.substring(0, lastTradingDay.indexOf('.')).trim() );
                    int dayOfWeek = Integer.parseInt( lastTradingDay.substring(lastTradingDay.indexOf('.')+1).trim() );
                    contract.lastTradingDayOfWeek = DayOfWeek.values()[dayOfWeek-1];
                }else{
                    contract.lastTradingDayOfMonth = ConversionUtil.toInt(lastTradingDay);
                }
            }
            List<MarketTimeRecord> marketTimes = new ArrayList<>();
            JsonArray marketTimesArray = (JsonArray)json.get("marketTimes");
            for(int j=0;j<marketTimesArray.size();j++) {
                JsonObject marketTimeInfo = (JsonObject)marketTimesArray.get(j);
                MarketTimeRecord timeRecord = new MarketTimeRecord();
                timeRecord.beginDate = DateUtil.str2localdate(marketTimeInfo.get("beginDate").getAsString());
                timeRecord.endDate = DateUtil.str2localdate(marketTimeInfo.get("endDate").getAsString());
                JsonArray timeFramesArray = (JsonArray)marketTimeInfo.get("timeFrames");
                timeRecord.timeStages = new MarketTimeSegment[ timeFramesArray.size()];
                for(int k=0;k<timeFramesArray.size();k++) {
                    String stageFrameStr = timeFramesArray.get(k).getAsString();
                    MarketTimeSegment stage = new MarketTimeSegment();
                    if ( stageFrameStr.startsWith("LTD:")) {
                        stageFrameStr = stageFrameStr.substring(4);
                        stage.lastTradingDay = true;
                        stage.marketType = MarketType.Night;
                    } else {
                        stage.marketType = MarketType.Day;
                    }
                    String stageFrames[] = StringUtil.split(stageFrameStr, ",|;");
                    stage.timeFrames = new LocalTime[stageFrames.length*2];
                    for(int l=0;l<stageFrames.length;l++) {
                        String frameTimes[] = StringUtil.split(stageFrames[l], "-");
                        stage.timeFrames[l*2] = DateUtil.str2localtime(frameTimes[0]);
                        stage.timeFrames[l*2+1] = DateUtil.str2localtime(frameTimes[1]);
                    }
                    timeRecord.timeStages[k] = stage;
                }
                marketTimes.add(timeRecord);
            }
            contract.marketTimeRecords = marketTimes.toArray(new MarketTimeRecord[marketTimes.size()]);

            for(String commodity:commodities) {
                Object lastValue = contracts.put(commodity.toUpperCase()+"."+exchange, contract);
                if ( lastValue!=null ) {
                    throw new RuntimeException("重复定义的合约 "+exchange+"."+commodity);
                }
            }
        }
    }

    private static String[] json2stringArray(JsonArray jsonArray)
    {
        String[] result = new String[jsonArray.size()];
        for(int i=0;i<jsonArray.size();i++) {
            result[i] = jsonArray.get(i).getAsString();
        }
        return result;
    }

}

