package trader.common.exchangeable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import trader.common.util.CSVDataSet;
import trader.common.util.CSVUtil;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.IOUtil;
import trader.common.util.StringUtil;

public class ExchangeableUtil {

    public static Map<String, LocalDateTime> getPredefinedTimes(ExchangeableTradingTimes tradingTimes, MarketType currType){
        Map<String, LocalDateTime> result = new LinkedHashMap<>();
        {
            LocalDateTime[] times = tradingTimes.getMarketTimes();
            result.put("$Open", times[0]);
            result.put("$O", times[0]);
            result.put("$Close", times[times.length-1]);
            result.put("$C", times[times.length-1]);
        }
        LocalDateTime[] nightTimes = tradingTimes.getMarketTimes(MarketType.Night);
        if ( nightTimes!=null ) {
            result.put("$NOpen", nightTimes[0]);
            result.put("$NightOpen", nightTimes[0]);
            result.put("$NClose", nightTimes[nightTimes.length-1]);
            result.put("$NightClose", nightTimes[nightTimes.length-1]);
        }
        LocalDateTime[] dayTimes = tradingTimes.getMarketTimes(MarketType.Day);
        if ( dayTimes!=null ) {
            result.put("$DOpen", dayTimes[0]);
            result.put("$DayOpen", dayTimes[0]);
            result.put("$DClose", dayTimes[dayTimes.length-1]);
            result.put("$DayClose", dayTimes[dayTimes.length-1]);
        }
        if ( currType!=null ) {
            LocalDateTime[] segTimes = tradingTimes.getMarketTimes(currType);
            if ( dayTimes!=null ) {
                result.put("$SOpen", segTimes[0]);
                result.put("$SegOpen", segTimes[0]);
                result.put("$SClose", segTimes[segTimes.length-1]);
                result.put("$SegClose", segTimes[segTimes.length-1]);
            }
        }
        return result;
    }

    public static LocalDateTime resolveTime(ExchangeableTradingTimes tradingTimes, MarketType currSeg, String timeExpr) {
        LocalDateTime result = null;

        if ( timeExpr.startsWith("$")) {
            LocalDateTime timeBase = null;
            Map<String, LocalDateTime> segTimes = getPredefinedTimes(tradingTimes, currSeg);
            for(String segKey:segTimes.keySet()) {
                if ( timeExpr.toLowerCase().startsWith(segKey.toLowerCase())) {
                    timeBase = segTimes.get(segKey);
                    timeExpr = timeExpr.substring(segKey.length()).toLowerCase();
                    break;
                }
            }
            if ( timeBase!=null ) {
                int timeAdjust=1;
                if ( timeExpr.startsWith("a")||timeExpr.startsWith("+")) {
                    //after
                    timeAdjust=1;
                    timeExpr = timeExpr.substring(1);
                }else if ( timeExpr.startsWith("b")||timeExpr.startsWith("-")) {
                    timeAdjust=-1;
                    timeExpr = timeExpr.substring(1);
                }
                long seconds = ConversionUtil.str2seconds(timeExpr);
                result = timeBase.plusSeconds(seconds*timeAdjust);
            }
        }else {
            LocalDateTime times[] = tradingTimes.getMarketTimes();
            result = DateUtil.str2localdatetime(timeExpr);
            if ( result==null ) {
                LocalTime time = DateUtil.str2localtime(timeExpr);
                for(int i=0;i<times.length;i+=2) {
                    LocalDateTime t0 = times[i];
                    LocalDateTime t1 = times[i+1];
                    if ( t0.toLocalDate().equals(t1.toLocalDate())) {
                        //不存在跨天
                        if ( t0.toLocalTime().compareTo(time)<=0 && t1.toLocalTime().compareTo(time)>=0 ) {
                            result = t0.toLocalDate().atTime(time);
                        }
                    } else {
                        //跨天
                        if ( t0.toLocalTime().compareTo(time)<=0 ) {
                            result = t0.toLocalDate().atTime(time);
                        }else if ( t1.toLocalTime().compareTo(time)>=0 ) {
                            result = t1.toLocalDate().atTime(time);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * 根据股票代码返回交易所
     */
    public static Exchange detectAShareStockExchange(String stockCode){
        if ( stockCode.startsWith("60")
                || stockCode.startsWith("70")
                )
        {
            return Exchange.SSE;
        }
        if ( stockCode.startsWith("00")
                ||stockCode.startsWith("30")
                )
        {
            return Exchange.SZSE;
        }
        throw new RuntimeException("Unknown exchange for "+stockCode);
    }

    public static Exchangeable match(Map<String,Exchangeable> map, String id)
    {
        if ( id.indexOf(".")<0 ){
            if ( id.toLowerCase().startsWith("sh") ){
                id = "sse."+id.substring(2);
            }else if ( id.toLowerCase().startsWith("sz")){
                id = "szse."+id.substring(2);
            }else if ( id.toLowerCase().startsWith("sze")){
                id = "szse."+id.substring(2);
            }
        }
        if ( id.indexOf(".")<0 ){

            LinkedList<Exchangeable> matched = new LinkedList<>();
            for(Exchangeable e:map.values()){
                if ( e.id().equals(id)){
                    matched.add(e);
                }
            }
            //search for stock in first
            for(Exchangeable e:matched){
                if ( e.getType()==ExchangeableType.STOCK ) {
                    return e;
                }
            }
            //search for other
            return matched.poll();
        }else{
            Exchangeable r = map.get(id.toLowerCase());
            if ( r!=null ){
                return r;
            }
            r = Exchangeable.fromString(id);
            id = r.toString();
            return map.get(id);
        }
    }

    public static List<Exchangeable> loadAllExchangeables(File dataDir) throws Exception
    {
        List<Exchangeable> result = new LinkedList<>();
        result.addAll(loadExchangeablesFromCsv(dataDir, Exchange.SSE));
        result.addAll(loadExchangeablesFromCsv(dataDir, Exchange.SZSE));
        result.addAll(loadExchangeablesFromCsv(dataDir, Exchange.CFFEX));

        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "cu"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "al"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "zn"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "pb"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "ni"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "sn"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "au"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "ag"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "rb"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "wr"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "hc"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "fu"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "bu"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "ru"));

        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "J"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "M"));
        result.addAll(Future.instrumentsFromMarketDay(MarketDayUtil.lastMarketDay(null, false), "Y"));

        return result;
    }

    public static Map<String,Exchangeable> loadAllExchangeablesAsMap(File securityDataDir)
            throws Exception
    {
        List<Exchangeable> all = loadAllExchangeables(securityDataDir);
        Map<String,Exchangeable> result = new LinkedHashMap<>();
        //Load security ids from security_list.csv
        for(Exchangeable e: all){
            result.put(e.id(), e);
            result.put(e.toString(), e);
        }
        return result;
    }

    private static List<Exchangeable> loadExchangeablesFromCsv(File securityDataDir, Exchange exchange) throws Exception
    {
        List<Exchangeable> result = new ArrayList<>(1024);
        File file = new File(securityDataDir,exchange.name()+"/"+ExchangeableData.SECURITY_LIST);
        if ( !file.exists() ){
            file = new File(securityDataDir,exchange.name()+"/"+ExchangeableData.EXCHANGEABLE_IDS);
        }
        if ( file.exists() ){
            CSVDataSet dataSet = CSVUtil.parse(IOUtil.createBufferedReader(file, StringUtil.UTF8), ',', true );
            while(dataSet.next()){
                result.add(Exchangeable.fromString(exchange.name(), dataSet.get("code"), dataSet.get("name")));
            }
        }
        return result;
    }

    public static List<Exchangeable> resolveExchangeables(String[] securityIds, Collection<Exchangeable> allExchangeables){
        return resolveExchangeables(securityIds, allExchangeables, true);
    }

    public static List<Exchangeable> resolveExchangeables(String[] securityIds, Collection<Exchangeable> allExchangeables, boolean throwExceptionIfNotMatched){
        List<Exchangeable> resolvedExchangeables = new LinkedList<>();
        if ( securityIds==null || (securityIds.length==1&&securityIds[0].equalsIgnoreCase("ALL")))
        {
            resolvedExchangeables.addAll(allExchangeables);
        } else if (securityIds.length==1&&securityIds[0].equalsIgnoreCase("HS300")){
            resolvedExchangeables.addAll( Arrays.<Exchangeable>asList(getHS300SecurityIds(allExchangeables)) );
        }else{
            for(int i=0;i<securityIds.length;i++){
                boolean matched = false;
                String pattern = securityIds[i];
                if ( pattern.endsWith("*") ){
                    String pattern2 = pattern.substring(0, pattern.length()-1);
                    for(Exchangeable secId:allExchangeables){
                        if ( secId.toString().toLowerCase().startsWith(pattern2.toLowerCase())){
                            if ( !resolvedExchangeables.contains(secId)) {
                                resolvedExchangeables.add(secId);
                            }
                            matched = true;
                        }
                    }
                }else if (pattern.equalsIgnoreCase("hs300")){
                    Exchangeable[] hs300 = getHS300SecurityIds(allExchangeables);
                    for(Exchangeable e:hs300){
                        if ( !resolvedExchangeables.contains(e)) {
                            resolvedExchangeables.add(e);
                        }
                    }
                    matched = true;
                }else{
                    Exchangeable sec = null;
                    if ( pattern.indexOf(".")<0 && (pattern.charAt(0)>='0' && pattern.charAt(0)<='9') ){
                        sec = new Security(Exchange.SSE,pattern);
                        if ( !allExchangeables.contains(sec) ) {
                            sec = new Security(Exchange.SZSE,pattern);
                        }
                    }else {
                        sec = Exchangeable.fromString(pattern);
                    }
                    if ( sec!=null ){
                        for(Exchangeable e:allExchangeables){
                            if (e.equals(sec)){
                                sec = e;
                                matched = true;
                                break;
                            }
                        }
                        matched = true;
                    }
                    if (!matched && sec!=null && sec.getType()==ExchangeableType.FUTURE ){
                        List<Future> instruments = Future.instrumentsFromMarketDay(LocalDate.now(), ((Future)sec).commodity());
                        if ( allExchangeables.contains(instruments.get(0))){
                            matched = true;
                        }
                    }
                    if ( matched && !resolvedExchangeables.contains(sec)) {
                        resolvedExchangeables.add(sec);
                    }
                }
                if ( !matched && throwExceptionIfNotMatched){
                    throw new IllegalArgumentException("No matched security id for : "+pattern);
                }
            }
        }
        return resolvedExchangeables;
    }

    public static Exchangeable[] getIndexAndETFIds(){
        return loadSecurityIds("security_index_etfs.txt");
    }

    public static Exchangeable[] getHS300SecurityIds(Collection<Exchangeable> allExchangeables){
        Exchangeable[] loadedResults = loadSecurityIds("security_hs300.txt");
        Exchangeable[] results = new Exchangeable[loadedResults.length];
        for(int i=0;i<loadedResults.length;i++ ){
            Exchangeable e = loadedResults[i];
            results[i] = e;
            for(Exchangeable e2:allExchangeables){
                if (e.equals(e2)){
                    results[i] = e2;
                    break;
                }
            }
        }
        return results;
    }

    private static Exchangeable[] loadSecurityIds(String resource){
        List<Exchangeable> result = new LinkedList<>();
        try(BufferedReader reader = IOUtil.createBufferedReader(loadResource(ExchangeableUtil.class,resource)))
        {
            String line = null;
            Exchange exchange = null;
            while( (line=reader.readLine())!=null ){
                line = line.trim();
                if ( line.length()==0 ){
                    continue;
                }
                if ( line.startsWith("[")){ // [sse]
                    line = line.substring(0,line.length()-1);
                    line = line.substring(1);
                    exchange = Exchange.getInstance(line);
                    continue;
                }
                result.add( (new Security(exchange,line)) );
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return result.toArray(new Exchangeable[result.size()]);
    }

    private static InputStream loadResource(Class clazz, String resource)
            throws IOException
    {
        InputStream is = clazz.getResourceAsStream(resource);
        if (is==null) {
            is = clazz.getResourceAsStream("/"+resource);
        }
        if (is==null) {
            is = clazz.getClassLoader().getResourceAsStream(resource);
        }
        if (is==null) {
            is = clazz.getClassLoader().getResourceAsStream("/"+resource);
        }
        if (is==null) {
            is = ClassLoader.getSystemResourceAsStream(resource);
        }
        if (is==null) {
            is = ClassLoader.getSystemResourceAsStream("/"+resource);
        }
        return is;
    }
}
