package trader.service.ta.bar;

import java.util.LinkedList;

import trader.service.md.MarketData;

/**
 * 固定时间滑动窗口
 */
public class TimedWindowTicksBar extends SlidingWindowTicksBar {

    /**
     * 最长时间窗口(毫秒)
     */
    protected int maxLifeTime;

    /**
     * @param maxLifeTime 最长保留时间(毫秒)
     */
    public TimedWindowTicksBar(int maxLifeTime) {
        this.maxLifeTime = maxLifeTime;
    }

    @Override
    protected boolean updateTicks(MarketData tick) {
        LinkedList<MarketData> ticks = this.ticks;
        boolean result = false;
        int lastTickTime = tick.mktTime;
        ticks.add(tick);

        //删除超时TICK
        while(!ticks.isEmpty()) {
            int currTickTime = ticks.peek().mktTime;
            if ( currTickTime+maxLifeTime>=lastTickTime) {
                break;
            }
            ticks.poll();
            result = true;
        }
        return result;
    }

}
