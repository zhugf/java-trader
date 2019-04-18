package trader.service.ta.bar;

import java.util.LinkedList;

import trader.service.md.MarketData;

/**
 * 固定成交量滑动窗口
 */
public class VolumeWindowTicksBar extends SlidingWindowTicksBar {

    private int maxVolume;

    public VolumeWindowTicksBar(int maxVolume) {
        this.maxVolume = maxVolume;
    }

    @Override
    protected boolean updateTicks(MarketData tick) {
        LinkedList<MarketData> ticks = this.ticks;
        ticks.add(tick);
        long lastVol = tick.volume;
        boolean result=false;
        while(true) {
            MarketData tick0 = ticks.peek();
            long currVol = lastVol - tick0.volume;
            if ( currVol>maxVolume && tick!=tick0 ) {
                ticks.poll();
                result = true;
                continue;
            }
            break;
        }
        return result;
    }

}
