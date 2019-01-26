package trader.service.trade;

/**
 * OrderRef报单生成接口
 */
public interface OrderRefGen {

    public String nextRefId(String accountId);

}
