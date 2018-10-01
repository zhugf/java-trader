package trader.service.trade;

import java.util.Map;

/**
 * 账户视图, 限定品种和保证金
 */
public interface AccountView {

    public String getId();

    /**
     * 最大手数, KEY: 品种前缀, VALUE: 最大持有数
     */
    public Map<String, Integer> getMaxVolumes();

    /**
     * 初始保证金占用
     */
    public long getInitMargin();

    /**
     * 当前保证金(已经包含手续费和当日浮动和实现盈亏计算)
     */
    public long getCurrMargin();
}
