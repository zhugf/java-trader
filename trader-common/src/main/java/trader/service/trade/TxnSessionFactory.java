package trader.service.trade;

/**
 * 可自动发现的实现工厂接口
 */
public interface TxnSessionFactory {

    public TxnSession create(TradeService tradeService, Account account);

}
