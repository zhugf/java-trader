package trader.service.tradlet.impl;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.PlaybookKeeper;
import trader.service.tradlet.PlaybookStateTuple;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletConstants;
import trader.service.tradlet.TradletContext;
import trader.service.tradlet.TradletGroup;

/**
 * 简单止损策略, 用于开仓后一段时间内止损, 需要Playbook属性中明确止损幅度.
 * <BR>目前使用软硬止损方式
 * <LI>软止损: 在某个价格之上保持一段时间即止损.
 * <LI>硬止损: 到达某个价格即立刻止损.
 * <LI>最长持仓时间: 到达最大持仓时间后, 即平仓
 * <LI>最后持仓时间: 到达某绝对市场时间, 即平仓
 *
 * 需要为每个playbook实例构建运行时数据, 保证tradlet重新加载后可用.
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "StopLoss")
public class SimpleStopLossTradlet implements Tradlet, TradletConstants {

    public static enum StopLossPolicy{
        HardPrice
        ,SoftPrice
        ,MaxLife
        ,EndTime
    }

    private BeansContainer beansContainer;
    private TradletGroup group;
    private PlaybookKeeper playbookKeeper;

    @Override
    public void init(TradletContext context) {
        beansContainer = context.getBeansContainer();
        group = context.getGroup();
        playbookKeeper = context.getGroup().getPlaybookKeeper();
    }

    @Override
    public void destroy() {

    }

    @Override
    public void onPlaybookStateChanged(Playbook pb, PlaybookStateTuple oldStateTuple) {
        if ( oldStateTuple==null ) {
            //从Playbook 属性构建运行时数据.
        }
    }

    @Override
    public void onTick(MarketData marketData) {

    }

    @Override
    public void onNewBar(LeveledTimeSeries series) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onNoopSecond() {
        // TODO Auto-generated method stub

    }

}
