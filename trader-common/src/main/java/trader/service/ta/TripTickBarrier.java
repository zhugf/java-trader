package trader.service.ta;

import java.util.ArrayList;
import java.util.List;

import trader.service.md.MarketData;

/**
 * 三重界限: 上下价格和时间. 当价格触及(包含)上下价格时, Barrier为Top/Bottom
 */
public class TripTickBarrier {
    /**
     * 界限
     */
    public static enum Barrier{
        Top
        ,Bottom
        ,End
    }
    private List<MarketData> ticks = new ArrayList<>();
    private int beginTime;
    private int maxTime;
    private long maxPrice;
    private long minPrice;
    private Barrier barrier;

    public TripTickBarrier(TripBarrierDef def, MarketData tick) {
        this(def.maxPrice, def.minPrice, def.maxTime, tick);
    }

    public TripTickBarrier(long maxPrice, long minPrice, int maxTime, MarketData tick) {
        this.maxPrice = maxPrice;
        this.minPrice = minPrice;
        this.maxTime = maxTime;
        this.beginTime = tick.mktTime;
        ticks.add(tick);
    }

    public MarketData getBeginTick() {
        return ticks.get(0);
    }

    public MarketData getEndTick() {
        return ticks.get(ticks.size()-1);
    }

    public List<MarketData> getTicks(){
        return ticks;
    }

    /**
     * 界限结果, 如果没有碰到返回null
     */
    public Barrier getBarrier() {
        return barrier;
    }

    public Barrier update(long currTime) {
        if ( currTime-beginTime>=maxTime ) {
            barrier= Barrier.End;
        }
        return barrier;
    }

    public Barrier update(MarketData tick) {
        ticks.add(tick);
        long lastPrice = tick.lastPrice;
        int lastTime = tick.mktTime;
        if ( lastPrice>=maxPrice ) {
            barrier = Barrier.Top;
            return barrier;
        }
        if ( lastPrice<=minPrice ) {
            barrier= Barrier.Bottom;
            return barrier;
        }
        if ( lastTime-beginTime>=maxTime ) {
            barrier= Barrier.End;
            return barrier;
        }
        return barrier;
    }

}
