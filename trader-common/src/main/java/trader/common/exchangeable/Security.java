package trader.common.exchangeable;

/**
 * 证券
 */
public class Security extends Exchangeable{
    /**
     * 沪深300.
     * <BR>SSE.000300
     */
    public static final Security HS300 = new Security(Exchange.SSE,"000300");

    public Security(Exchange exchange, String id){
        super(exchange,id);
    }

    public Security(Exchange exchange,String id, String name){
        super(exchange,id,name);
    }

}
