package trader.service.trade;

import java.util.Collection;
import java.util.Map;

import trader.common.exchangeable.Exchangeable;

/**
 * 账户视图, 限定品种和保证金.
 */
public interface AccountView {

    public String getId();

    public Account getAccount();

    /**
     * 最大手数限制, KEY: 品种, VALUE: 最大持有数
     */
    public Map<Exchangeable, Integer> getMaxVolumes();

    /**
     * 保证金占用限制
     */
    public long getMaxMargin();

    /**
     * 当前保证金占用(已经包含手续费和当日浮动和实现盈亏计算)
     */
    public long getCurrMargin();

    /**
     * 所关联的持仓
     */
    public Collection<Position> getPositions();

    public Position getPosition(Exchangeable e);
}
