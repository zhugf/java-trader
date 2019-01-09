package trader.simulator.trade;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.service.trade.Account;
import trader.service.trade.TxnSession;
import trader.service.trade.TxnSessionFactory;
import trader.service.trade.spi.TxnSessionListener;

@Discoverable(interfaceClass = TxnSessionFactory.class, purpose = TxnSession.PROVIDER_SIM)
public class SimTxnSessionFactory implements TxnSessionFactory {

    @Override
    public TxnSession create(BeansContainer beansContainer, Account account, TxnSessionListener listener) {
        return new SimTxnSession(beansContainer, account, listener);
    }

}
