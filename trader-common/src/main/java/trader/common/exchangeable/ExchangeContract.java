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
    private String[] commodities;

    private String[] instruments;

    private String instrumentFormat = "YYMM";

    private DayOfWeek lastTradingDayOfWeek;

    private int lastTradingDayOfMonth;

    private int lastTradingWeekOfMonth;

    /**
     * 每天交易时间段
     */
    private TimeStage[] timeStages;

    private double priceTick = 0.01;

    public double getPriceTick() {
        return priceTick;
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

