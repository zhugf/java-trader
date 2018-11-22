package trader.service.trade.ctp;

import trader.service.trade.Account;
import trader.service.trade.AccountImpl;
import trader.service.trade.TradeService;
import trader.service.trade.TradeServiceImpl;
import trader.service.trade.TxnSession;
import trader.service.trade.TxnSessionFactory;

public class CtpTxnSessionFactory implements TxnSessionFactory {

    @Override
    public TxnSession create(TradeService tradeService, Account account) {
        return new CtpTxnSession((TradeServiceImpl)tradeService, (AccountImpl)account);
    }

}
