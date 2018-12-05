package trader.service.trade;

import trader.common.beans.BeansContainer;
import trader.service.trade.spi.TxnSessionListener;

/**
 * 可自动发现的实现工厂接口
 */
public interface TxnSessionFactory {

    public TxnSession create(BeansContainer beansContainer, Account account, TxnSessionListener listner);

}
