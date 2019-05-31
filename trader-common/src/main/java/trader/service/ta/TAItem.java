package trader.service.ta;

import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;

@SuppressWarnings("rawtypes")
public interface TAItem {

    public Exchangeable getExchangeable();

    /**
     * 获得某个品种的KBar数据, 如果没有返回null.
     * <BR>要求品种必须是关注行情的品种; 不支持运行到一半时动态增加品种的kBar
     * <BR>对于MIN1以外的KBar, 会动态从MIN1合成
     */
    public LeveledTimeSeries getSeries(PriceLevel level);

}
