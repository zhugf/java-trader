package trader.common.exchangeable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.exchangeable.Exchange.MarketType;
import trader.common.util.DateUtil;
import trader.common.util.IOUtil;
import trader.common.util.StringUtil;

/**
 * 交易所合约信息
 */
public class ExchangeContract {

    public static class TimeStage {
        MarketType marketType;
        LocalTime[] timeFrames;
        LocalDate beginDate;
        LocalDate endDate;
        public MarketType getMarketType() {
            return marketType;
        }
        public LocalTime[] getTimeFrames() {
            return timeFrames;
        }
        public LocalDate getBeginDate() {
            return beginDate;
        }
        public LocalDate getEndDate() {
            return endDate;
        }
    }

    private String[] instruments;

    private String instrumentFormat = "YYMM";

    private DayOfWeek lastTradingDayOfWeek;

    private int lastTradingDayOfMonth;

    private int lastTradingWeekOfMonth;

    /**
     * 每天交易时间段
     */
    private TimeStage[] timeStages;

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
    public TimeStage[] getTimeStages() {
        return timeStages;
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

    public static Map<String, ExchangeContract> getContracts() {
        return contracts;
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
            if ( json.has("instruments") ) {
                contract.instruments = json2stringArray((JsonArray)json.get("instruments"));
            }
            if ( json.has("instrumentFormat")) {
                contract.instrumentFormat = json.get("instrumentFormat").getAsString();
            }
            if ( json.has("lastTradingDay")) {

                String lastTradingDay = json.get("lastTradingDay").getAsString();
                if ( lastTradingDay.indexOf(".")>0){
                    contract.lastTradingWeekOfMonth = Integer.parseInt( lastTradingDay.substring(0, lastTradingDay.indexOf('.')).trim() );
                    int dayOfWeek = Integer.parseInt( lastTradingDay.substring(lastTradingDay.indexOf('.')+1).trim() );
                    contract.lastTradingDayOfWeek = DayOfWeek.values()[dayOfWeek-1];
                }else{
                    contract.lastTradingDayOfMonth = Integer.parseInt(lastTradingDay.trim());
                }
            }
            List<TimeStage> marketTimes = new ArrayList<>();
            JsonArray marketTimesArray = (JsonArray)json.get("marketTimes");
            for(int j=0;j<marketTimesArray.size();j++) {
                JsonObject marketTimeInfo = (JsonObject)marketTimesArray.get(j);
                TimeStage timeStage = new TimeStage();
                timeStage.marketType = MarketType.valueOf(marketTimeInfo.get("marketType").getAsString());
                timeStage.beginDate = DateUtil.str2localdate(marketTimeInfo.get("beginDate").getAsString());
                timeStage.endDate = DateUtil.str2localdate(marketTimeInfo.get("endDate").getAsString());
                JsonArray timeFramesArray = (JsonArray)marketTimeInfo.get("timeFrames");
                timeStage.timeFrames = new LocalTime[ timeFramesArray.size()*2 ];
                for(int k=0;k<timeFramesArray.size();k++) {
                    String timeFrame = timeFramesArray.get(k).getAsString();
                    String fp[] = StringUtil.split(timeFrame, "-");
                    timeStage.timeFrames[k*2] = DateUtil.str2localtime(fp[0]);
                    timeStage.timeFrames[k*2+1] = DateUtil.str2localtime(fp[1]);
                    if ( timeStage.timeFrames[k*2]==null || timeStage.timeFrames[k*2+1]==null ) {
                        System.out.println("Contract "+Arrays.asList(contract.instruments)+" time stage is invalid: "+timeFrame);
                    }
                }
                marketTimes.add(timeStage);
            }
            contract.timeStages = marketTimes.toArray(new TimeStage[marketTimes.size()]);

            for(String commodity:commodities) {
                Object lastValue = contracts.put(exchange+"."+commodity, contract);
                if ( lastValue!=null ) {
                    throw new RuntimeException("重复定义的合约 "+exchange+"."+commodity);
                }
            }
        }
    }

    static ExchangeContract matchContract(Exchange exchange, String instrument) {
        //证券交易所, 找 sse.* 这种
        if ( exchange.isSecurity() ) {
            return contracts.get(exchange.name().toLowerCase()+".*");
        }
        //期货交易所, 找 cffex.TF1810, 找cffex.TF, 再找 cffex.*
        ExchangeContract contract = contracts.get(exchange.name()+"."+instrument);
        if ( contract==null ) {
            StringBuilder commodity = new StringBuilder(10);
            for(int i=0;i<instrument.length();i++) {
                char ch = instrument.charAt(i);
                if ( ch>='0' && ch<='9' ) {
                    break;
                }
                commodity.append(ch);
            }
            contract = contracts.get(exchange.name()+"."+commodity);
        }
        if ( contract==null ) {
            contract = contracts.get(exchange.name()+".*");
        }
        return contract;
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

