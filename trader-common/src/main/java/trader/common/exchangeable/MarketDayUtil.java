package trader.common.exchangeable;

import java.io.BufferedReader;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import trader.common.util.DateUtil;
import trader.common.util.IOUtil;

public class MarketDayUtil {

    private static final Map<String,List<LocalDate>> closeDayMap = new HashMap<>();
    static{
        loadCloseDayMap();
    }

    private static void loadCloseDayMap(){
        try(BufferedReader reader = IOUtil.createBufferedReader(MarketDayUtil.class.getResourceAsStream("marketCloseDays.txt"));)
        {
            String line = null;
            List<LocalDate> closeDays = null;
            String exchangeLine = null;
            while( (line=reader.readLine())!=null ){
                line = line.trim();
                if ( line.length()==0 || line.startsWith("#")) {
                    continue;
                }
                if ( line.length()!=8 || line.indexOf(",")>0 ){
                    if ( exchangeLine!=null ){
                        String[] exchanges = exchangeLine.split(",");
                        for(int i=0;i<exchanges.length;i++){
                            Exchange.getInstance(exchanges[i]); //检验Exchange是否支持
                            closeDayMap.put(exchanges[i], closeDays);
                        }
                    }
                    exchangeLine = line;
                    closeDays = new LinkedList<>();
                }else{
                    closeDays.add(DateUtil.str2localdate(line));
                }
            }
            if ( closeDays.size()>0 ){
                String[] exchanges = exchangeLine.split(",");
                for(int i=0;i<exchanges.length;i++){
                    closeDayMap.put(exchanges[i], closeDays);
                }
            }
            reader.close();
        }catch(Throwable ioe){
            throw new RuntimeException(ioe);
        }
    }

    private static LocalDate nextWorkingDay(LocalDate tradingDay, boolean nextOrPrev) {
        while(true){
            tradingDay = tradingDay.plusDays(nextOrPrev?1:-1);
            DayOfWeek dayOfWeek = tradingDay.getDayOfWeek();
            if ( dayOfWeek == DayOfWeek.SUNDAY || dayOfWeek==DayOfWeek.SATURDAY) {
                continue;
            }
            break;
        }
        return tradingDay;
    }

    public static LocalDate[] getMarketDays(Exchange exchange, LocalDate beginDay, LocalDate endDay){
        List<LocalDate> result = new LinkedList<>();
        if ( exchange==null ) {
            exchange = Exchange.SSE;
        }
        if ( endDay==null ) {
            endDay = lastMarketDay(exchange, false);
        }
        List<LocalDate> closeDays = closeDayMap.get(exchange.name());
        LocalDate currTradingDay = beginDay;

        while(true){
            if ( currTradingDay.isAfter(endDay)) {
                break;
            }
            DayOfWeek dayOfWeek = currTradingDay.getDayOfWeek();
            if ( dayOfWeek.getValue() >= DayOfWeek.MONDAY.getValue() && dayOfWeek.getValue()<=DayOfWeek.FRIDAY.getValue() ){
                if ( closeDays!=null && !closeDays.contains(currTradingDay) ) {
                    result.add(currTradingDay);
                }
            }
            currTradingDay = currTradingDay.plusDays(1);
        }
        return result.toArray(new LocalDate[result.size()]);
    }

    public static LocalDate computeMarketDay(Exchange exchange, LocalDate day, int toAdd){
        if (toAdd==0) {
            return day;
        }
        if (toAdd>0){
            for(int i=0;i<toAdd;i++){
                day = nextMarketDay(exchange, day );
            }
            return day;
        }else if ( toAdd<0 ){
            for(int i=0;i<Math.abs(toAdd);i++){
                day = prevMarketDay(exchange, day );
            }
            return day;
        }
        return null;
    }

    public static LocalDate prevMarketDay(Exchange exchange, LocalDate tradingDay){
        if ( exchange==null ) {
            exchange = Exchange.SSE;
        }
        List<LocalDate> closeDays = closeDayMap.get(exchange.name());

        while(true){
            tradingDay = tradingDay.plusDays(-1);
            DayOfWeek dayOfWeek = tradingDay.getDayOfWeek();
            if ( dayOfWeek == DayOfWeek.SUNDAY || dayOfWeek==DayOfWeek.SATURDAY) {
                continue;
            }
            if ( closeDays!=null && closeDays.contains(tradingDay) ) {
                continue;
            }
            return tradingDay;
        }
    }


    /**
     * 上一个交易日
     *
     * @param exchange
     * @param completed true 只有当前交易日收市, 才返回当前交易日
     * @return
     */
    public static LocalDate lastMarketDay(Exchange exchange, boolean completed ){
        if ( exchange==null ) {
            exchange = Exchange.SSE;
        }
        List<LocalDate> closeDays = closeDayMap.get(exchange.name());
        LocalDateTime tradingDateTime = DateUtil.getCurrentTime();
        DayOfWeek dayOfWeek = tradingDateTime.getDayOfWeek();
        if ( !exchange.isFuture() ) {
            //股票没有夜市
            LocalTime tradingTime = tradingDateTime.toLocalTime();
            if ( completed ) {
                if ( tradingTime.isBefore(exchange.getMarketTimes()[0]) ){
                    tradingDateTime = tradingDateTime.plusDays(-1);
                }
            }
            while(true){
                dayOfWeek = tradingDateTime.getDayOfWeek();
                if ( dayOfWeek == DayOfWeek.SUNDAY || dayOfWeek==DayOfWeek.SATURDAY){
                    tradingDateTime = tradingDateTime.plusDays(-1);
                    continue;
                }
                if ( closeDays!=null && closeDays.contains(tradingDateTime.toLocalDate()) ){
                    tradingDateTime = tradingDateTime.plusDays(-1);
                    continue;
                }
                return tradingDateTime.toLocalDate();
            }
        }else { //期货有夜市, 夜市的交易日是下一日
            if ( completed && (dayOfWeek==DayOfWeek.MONDAY
                    ||dayOfWeek==DayOfWeek.TUESDAY
                    ||dayOfWeek==DayOfWeek.WEDNESDAY
                    ||dayOfWeek==DayOfWeek.TUESDAY)){
                LocalTime tradingTime = tradingDateTime.toLocalTime();
                if ( tradingTime.isBefore(exchange.getMarketTimes()[1]) ){
                    tradingDateTime = tradingDateTime.plusDays(1);
                }
            }
            while(true){
                dayOfWeek = tradingDateTime.getDayOfWeek();
                if ( dayOfWeek == DayOfWeek.SUNDAY || dayOfWeek==DayOfWeek.SATURDAY){
                    tradingDateTime = tradingDateTime.plusDays(-1);
                    continue;
                }
                if ( closeDays!=null && closeDays.contains(tradingDateTime.toLocalDate()) ){
                    tradingDateTime = tradingDateTime.plusDays(-1);
                    continue;
                }
                return tradingDateTime.toLocalDate();
            }
        }
    }

    public static boolean isMarketDay(Exchange exchange, LocalDate tradingDay){
        if ( exchange==null ) {
            exchange = Exchange.SSE;
        }
        List<LocalDate> closeDays = closeDayMap.get(exchange.name());
        DayOfWeek dayOfWeek = tradingDay.getDayOfWeek();
        if ( dayOfWeek == DayOfWeek.SUNDAY || dayOfWeek==DayOfWeek.SATURDAY){
            return false;
        }
        if ( closeDays!=null && closeDays.contains(tradingDay) ) {
            return false;
        }
        return true;
    }

    public static LocalDate nextMarketDay(Exchange exchange, LocalDate tradingDay){
        if ( exchange==null ) {
            exchange = Exchange.SSE;
        }
        List<LocalDate> closeDays = closeDayMap.get(exchange.name());
        while(true){
            tradingDay = tradingDay.plusDays(1);
            DayOfWeek dayOfWeek = tradingDay.getDayOfWeek();
            if ( dayOfWeek == DayOfWeek.SUNDAY || dayOfWeek==DayOfWeek.SATURDAY) {
                continue;
            }
            if ( closeDays!=null && closeDays.contains(tradingDay) ) {
                continue;
            }
            return tradingDay;
        }
    }

    public static LocalDate thisOrNextMarketDay(Exchange exchange, LocalDate tradingDay, boolean thisCompleted){
        if ( exchange==null) {
            exchange = Exchange.SSE;
        }
        if( tradingDay==null ) {
            tradingDay = LocalDate.now(exchange.getZoneId());
        }
        LocalTime thisTime = LocalTime.now(exchange.getZoneId());
        if ( thisCompleted && thisTime.compareTo(exchange.getMarketTimes()[1])<=0 ){
            tradingDay = tradingDay.plusMonths(1);
        }
        if ( isMarketDay(exchange, tradingDay) ) {
            return tradingDay;
        }
        return nextMarketDay(exchange, tradingDay);
    }

}
