package trader.common.exchangeable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
        JSONArray jsonArray = new JSONArray( IOUtil.readAsString(ExchangeContract.class.getResourceAsStream("exchangeContracts.json")) );
        for(int i=0;i<jsonArray.length();i++) {
            JSONObject json = jsonArray.getJSONObject(i);
            String exchange = json.getString("exchange");
            String commodities[] = json2stringArray( json.getJSONArray("commodity") );

            ExchangeContract contract = new ExchangeContract();
            if ( json.has("instruments") ) {
                contract.instruments = json2stringArray(json.getJSONArray("instruments"));
            }
            if ( json.has("instrumentFormat")) {
                contract.instrumentFormat = json.getString("instrumentFormat");
            }
            if ( json.has("lastTradingDay")) {

                String lastTradingDay = json.getString("lastTradingDay");
                if ( lastTradingDay.indexOf(".")>0){
                    contract.lastTradingWeekOfMonth = Integer.parseInt( lastTradingDay.substring(0, lastTradingDay.indexOf('.')).trim() );
                    int dayOfWeek = Integer.parseInt( lastTradingDay.substring(lastTradingDay.indexOf('.')+1).trim() );
                    contract.lastTradingDayOfWeek = DayOfWeek.values()[dayOfWeek-1];
                }else{
                    contract.lastTradingDayOfMonth = Integer.parseInt(lastTradingDay.trim());
                }
            }
            List<TimeStage> marketTimes = new ArrayList<>();
            JSONArray marketTimesArray = json.getJSONArray("marketTimes");
            for(int j=0;j<marketTimesArray.length();j++) {
                JSONObject marketTimeInfo = marketTimesArray.getJSONObject(j);
                TimeStage timeStage = new TimeStage();
                timeStage.marketType = MarketType.valueOf(marketTimeInfo.getString("marketType"));
                timeStage.beginDate = DateUtil.str2localdate(marketTimeInfo.getString("beginDate"));
                timeStage.endDate = DateUtil.str2localdate(marketTimeInfo.getString("endDate"));
                JSONArray timeFramesArray = marketTimeInfo.getJSONArray("timeFrames");
                timeStage.timeFrames = new LocalTime[ timeFramesArray.length()*2 ];
                for(int k=0;k<timeFramesArray.length();k++) {
                    String timeFrame = timeFramesArray.getString(k);
                    String fp[] = StringUtil.split(timeFrame, "-");
                    timeStage.timeFrames[k*2] = DateUtil.str2localtime(fp[0]);
                    timeStage.timeFrames[k*2+1] = DateUtil.str2localtime(fp[1]);
                }
                marketTimes.add(timeStage);
            }
            contract.timeStages = marketTimes.toArray(new TimeStage[marketTimes.size()]);

            for(String commodity:commodities) {
                contracts.put(exchange+"."+commodity, contract);
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

    private static String[] json2stringArray(JSONArray jsonArray) throws JSONException
    {
        String[] result = new String[jsonArray.length()];
        for(int i=0;i<jsonArray.length();i++) {
            result[i] = jsonArray.get(i).toString();
        }
        return result;
    }

}

