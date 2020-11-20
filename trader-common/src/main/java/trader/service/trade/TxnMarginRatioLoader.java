package trader.service.trade;

import java.util.Collection;
import java.util.Map;

import trader.common.beans.Lifecycle;
import trader.common.exchangeable.Exchangeable;

/**
 * 合约保证金比率加载
 */
public interface TxnMarginRatioLoader extends Lifecycle{

    /**
     * 加载
     */
    public Map<Exchangeable, double[]> load(Collection<Exchangeable> instruments) throws Exception;

}
