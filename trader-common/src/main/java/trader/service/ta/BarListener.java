package trader.service.ta;

import trader.common.exchangeable.Exchangeable;

/**
 * KBar 更新侦听函数
 */
public interface BarListener {

    public void onNewBar(Exchangeable e, LeveledBarSeries series);

}
