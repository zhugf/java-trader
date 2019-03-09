package trader.common.exchangeable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;

public abstract class Exchangeable implements Comparable<Exchangeable> {

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
        if ( name==null ) {
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
    public String commodity(){
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
            type = ExchangeableType.FUTURE;
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
    public static Exchangeable fromString(String str){
        Exchangeable result = null;
        result = cachedExchangeables.get(str);
        if ( result!=null ) {
            return result;
        }

        int idx = str.indexOf('.');
        if ( idx<0 ){
            result = Future.fromInstrument(str);
        }else{
            String exchangeName = str.substring(0,idx);
            String id = str.substring(idx+1);
            Exchange exchange = Exchange.getInstance(exchangeName);
            if ( exchange==null ){
                String tmp = id;
                id = exchangeName;
                exchangeName = tmp;
                exchange = Exchange.getInstance(exchangeName);
            }
            if ( exchange!=null ) {
                if ( exchange.isSecurity() ){
                    result = new Security(exchange, id);
                }else if ( exchange.isFuture() ){
                    result = new Future(exchange, id);
                }
            }
            if (result == null) {
                throw new RuntimeException("Unknown exchangeable string: " + str);
            }
        }

        cachedExchangeables.put(str, result);
        return result;
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
            result = Future.fromString(uniqueStr);
        }else{
            Exchange exchange = Exchange.getInstance(exchangeStr);

            if ( exchange==Exchange.SSE || exchange==Exchange.SZSE ){
                result = new Security(exchange, instrumentStr, instrumentName);
            }else if ( exchange==Exchange.CFFEX || exchange==Exchange.SHFE || exchange==Exchange.DCE || exchange==Exchange.CZCE || exchange==Exchange.INE ){
                result = new Future(exchange, instrumentStr, instrumentName);
            }else{
                throw new RuntimeException("Unknown exchangeable string: "+uniqueStr);
            }
        }
        cachedExchangeables.put(uniqueStr, result);
        if ( result.getType()==ExchangeableType.FUTURE ) {
            cachedExchangeables.put(instrumentStr, result);
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
        return uniqueId.compareTo(o.uniqueId);
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
