package trader.common.exchangeable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;

public abstract class Exchangeable implements Comparable<Exchangeable>, JsonEnabled {

    protected Exchange exchange;
    protected String id;
    protected ExchangeableType type;
    protected String uniqueId;
    protected String name;

    /**
     * 只在当前JVM有效的唯一递增INT值
     */
    protected transient int uniqueIntId;

    protected Exchangeable(Exchange exchange, String id){
        this(exchange, id, id);
    }

    protected Exchangeable(Exchange exchange, String id, String name){
        this.exchange = exchange;
        this.id = id;
        this.name = name;
        if ( StringUtil.isEmpty(name) ) {
            this.name = id;
        }
        this.type = detectType();
        uniqueId = id+"."+exchange.name();
        uniqueIntId = genUniqueIntId(uniqueId);
    }

    /**
     * 名称
     */
    public String name(){
        return name;
    }

    /**
     * ID,对于期货.是品种+合约的全名
     */
    public String id(){
        return id;
    }

    public String uniqueId(){
        return uniqueId;
    }

    /**
     * Integer ID
     */
    public int uniqueIntId(){
        return uniqueIntId;
    }

    /**
     * 期货品种名称, 或者股票ID
     */
    public String contract(){
        return id;
    }

    public Exchange exchange(){
        return exchange;
    }

    public ExchangeableType getType(){
        return type;
    }

    public String toPrintableString(){
        if (name==null) {
            return uniqueId;
        } else {
            return uniqueId+" "+name;
        }
    }

    public long getPriceTick() {
        return PriceUtil.price2long(0.01);
    }

    public int getVolumeMutiplier() {
        return 1;
    }

    @Override
    public String toString(){
        return uniqueId;
    }

    @Override
    public boolean equals(Object o){
        if ( this==o ) {
            return true;
        }
        if ( o==null || !(o instanceof Exchangeable) ){
            return false;
        }
        Exchangeable s = (Exchangeable)o;
        return uniqueIntId == s.uniqueIntId;
    }

    @Override
    public int hashCode(){
        return uniqueId.hashCode();
    }

    public JsonElement toJson() {
        return new JsonPrimitive(toString());
    }

    protected ExchangeableType detectType(){
        ExchangeableType type = ExchangeableType.OTHER;
        if ( exchange==Exchange.SSE ){
            if ( id.startsWith("00") ){
                type = ExchangeableType.INDEX;
            }else if ( id.startsWith("01") ){
                type = ExchangeableType.BOND;
            }else if ( id.startsWith("11") ){
                type = ExchangeableType.CONVERTABLE_BOND;
            }else if ( id.startsWith("12") ){
                type=ExchangeableType.BOND;
            }else if ( id.startsWith("20") ){
                type=ExchangeableType.BOND_REPURCHARSE;
            }else if ( id.startsWith("50") || id.startsWith("51") ){
                type=ExchangeableType.FUND;
            }else if ( id.startsWith("60") ){
                type=ExchangeableType.STOCK;
            }else if ( id.startsWith("90") ){//B股
                type=ExchangeableType.STOCK;
            }
        }else if ( exchange==Exchange.SZSE ){
            if ( id.startsWith("00")){
                type = ExchangeableType.STOCK;
            }else if ( id.startsWith("10")||id.startsWith("11") ){
                type = ExchangeableType.BOND;
            }else if ( id.startsWith("12") ){
                type = ExchangeableType.CONVERTABLE_BOND;
            }else if ( id.startsWith("13") ){
                type = ExchangeableType.BOND_REPURCHARSE;
            }else if ( id.startsWith("15") || id.startsWith("16") ){
                type = ExchangeableType.FUND;
            }else if ( id.startsWith("20")){ //B股
                type = ExchangeableType.STOCK;
            }else if ( id.startsWith("30")){ //创业板
                type = ExchangeableType.STOCK;
            }else if ( id.startsWith("39") ){
                type = ExchangeableType.INDEX;
            }
        }else if (exchange ==Exchange.HKEX) {

            if (id.startsWith("99")) { //沪股通990001, 深股通990002
                type = ExchangeableType.INDEX;
            }
        } else if ( exchange==Exchange.CFFEX
                || exchange==Exchange.DCE
                || exchange==Exchange.SHFE
                || exchange==Exchange.CZCE
                || exchange==Exchange.INE
                )
        { //期货
            if ( id.indexOf('&')>0 ) {
                type = ExchangeableType.FUTURE_COMBO;
            } else if ( id.indexOf("-P-")>0 || id.indexOf("-C-")>0 ){
                type = ExchangeableType.OPTION;
            } else {
                type = ExchangeableType.FUTURE;
            }
        }
        return type;
    }

    public static Exchangeable create(Exchange exchange, String instrumentId){
        return create(exchange, instrumentId, null);
    }

    public static Exchangeable create(Exchange exchange, String instrumentId, String name){
        if ( exchange==Exchange.SSE || exchange==Exchange.SZSE || exchange==Exchange.DCE || exchange==Exchange.CZCE ){
            return new Security(exchange, instrumentId, name);
        }else if ( exchange==Exchange.CFFEX|| exchange==Exchange.DCE || exchange==Exchange.CZCE || exchange==Exchange.SHFE ){
            return new Future(exchange, instrumentId, name);
        }else if ( exchange==null ){
            return Future.fromString(instrumentId);
        }
        throw new RuntimeException("Unknown exchange: "+exchange);
    }

    private static Map<String, Exchangeable> cachedExchangeables = new HashMap<>();

    /**
     * Load exchangeable from cache
     */
    public static Exchangeable fromString(String exchangeableStr){
        String exchangeName = null;
        String instrumentStr = exchangeableStr;
        int dotIdx = instrumentStr.indexOf('.');
        if ( dotIdx>0) {
            exchangeName = instrumentStr.substring(0,dotIdx);
            instrumentStr = instrumentStr.substring(dotIdx+1);
            Exchange exchange = Exchange.getInstance(exchangeName);
            if ( exchange==null ){
                String tmp = instrumentStr;
                instrumentStr = exchangeName;
                exchangeName = tmp;
                exchange = Exchange.getInstance(exchangeName);
            }
        }
        return fromString(exchangeName, instrumentStr, null);
    }

    /**
     * Load exchangeable from cache
     */
    public static Exchangeable fromString(String exchangeStr, String instrumentStr){
        return fromString(exchangeStr, instrumentStr, null);
    }

    /**
     * Load exchangeable from cache
     */
    public static Exchangeable fromString(String exchangeStr, String instrumentStr, String instrumentName){
        String uniqueStr = null;
        if ( !StringUtil.isEmpty(exchangeStr) ) {
            uniqueStr = instrumentStr+"."+exchangeStr;
        } else {
            uniqueStr = instrumentStr;
        }

        Exchangeable result = cachedExchangeables.get(uniqueStr);
        if ( result!=null ) {
            return result;
        }

        if ( exchangeStr==null ){
            if ( FutureCombo.acceptId(instrumentStr) ) {
                result = new FutureCombo(FutureCombo.detectExchange(instrumentStr), instrumentStr, instrumentName);
            } else if ( Option.acceptId(instrumentStr)){
                result = new Option(Option.detectExchange(instrumentStr), instrumentStr, instrumentName);
            } else {
                result = new Future(Future.detectExchange(instrumentStr), instrumentStr, instrumentName);
            }
        }else{
            Exchange exchange = Exchange.getInstance(exchangeStr);
            if ( exchange==Exchange.SSE || exchange==Exchange.SZSE ){
                result = new Security(exchange, instrumentStr, instrumentName);
            }else if ( exchange==Exchange.CFFEX || exchange==Exchange.SHFE || exchange==Exchange.DCE || exchange==Exchange.CZCE || exchange==Exchange.INE ){
                if ( FutureCombo.acceptId(instrumentStr) ) {
                    result = new FutureCombo(exchange, instrumentStr, instrumentName);
                }else {
                    result = new Future(exchange, instrumentStr, instrumentName);
                }
            }else{
                throw new RuntimeException("Unknown exchangeable string: "+uniqueStr);
            }
        }
        cachedExchangeables.put(uniqueStr, result);
        switch(result.getType()){
        case FUTURE:
        case FUTURE_COMBO:
            cachedExchangeables.put(instrumentStr, result);
            break;
        }
        return result;
    }

    /**
     * Update cache with pre-created entries.
     * <BR>出于多线程冲突原因, 必须在初始化时调用
     */
    public static void populateCache(Collection<Exchangeable> instruments)
    {
        if ( instruments==null ){
            return;
        }
        for(Exchangeable e:instruments){
            cachedExchangeables.put(e.toString(), e);
        }
    }

    /**
     * 结算周期
     * <BR>0 = T+0
     * <BR>1 = T+1
     */
    public int getSettlementPeriod(){
        if ( exchange==Exchange.SSE || exchange==Exchange.SZSE ){
            switch( getType()) {
            case BOND:
            case OPTION:
                return 0;
            default:
                return 1;
            }
        }else{
            return 0;
        }
    }

    @Override
    public int compareTo(Exchangeable o)
    {
        if ( o.exchange()!=exchange() ) {
            return exchange().compareTo(o.exchange());
        } else {
            return compareId(o);
        }
    }

    protected int compareId(Exchangeable o) {
        return this.id().compareTo(o.id());
    }

    private static AtomicInteger nextExchangeableId = new AtomicInteger();
    private static Map<String, Integer> exchangeableIds = new HashMap<>();
    private static int genUniqueIntId(String uniqueId){
        Integer id = null;
        synchronized(exchangeableIds){
            id = exchangeableIds.get(uniqueId);
            if ( id==null ){
                id = nextExchangeableId.getAndIncrement();
                exchangeableIds.put(uniqueId, id);
            }
        }
        return id;
    }

    /**
     * 港股通
     */
    public static Exchangeable HKEX_GGT = new Security(Exchange.HKEX, "990001", "港股通");
    /**
     * 深股通
     */
    public static Exchangeable HKEX_SGT = new Security(Exchange.HKEX, "990002", "深股通");
}
