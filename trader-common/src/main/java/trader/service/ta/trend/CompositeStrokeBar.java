package trader.service.ta.trend;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.DateUtil;
import trader.service.md.MarketData;
import trader.service.ta.LongNum;

/**
 * 复合笔划, 处理同向笔划的包含关系
 */
public class CompositeStrokeBar<T> extends WaveBar<T> {
    private static final long serialVersionUID = 2443723183855946643L;

    protected List<WaveBar<T>> bars;

    public CompositeStrokeBar(WaveBar<T> stroke1, WaveBar<T> stroke2) {
        bars = new ArrayList<>(2);
        bars.add(stroke1);
        bars.add(stroke2);
        if ( stroke1.getDirection()!=stroke2.getDirection() ) {
            throw new RuntimeException("NOT sampe stroke direction: "+stroke1+" "+stroke2);
        }
        this.direction = stroke1.getDirection();
        update(null, null);
    }

    @Override
    public WaveType getWaveType() {
        return WaveType.Stroke;
    }

    @Override
    public List<WaveBar> getBars() {
        return Collections.unmodifiableList(bars);
    }

    @Override
    public int getBarCount() {
        return bars.size();
    }

    @Override
    public MarketData getOpenTick() {
        return bars.get(0).getOpenTick();
    }

    @Override
    public MarketData getCloseTick() {
        return bars.get(bars.size()-1).getCloseTick();
    }

    @Override
    public MarketData getMaxTick() {
        return null;
    }

    @Override
    public MarketData getMinTick() {
        return null;
    }

    @Override
    public Duration getTimePeriod() {
        Duration result = null;
        for(WaveBar bar:bars) {
            if ( result==null) {
                result = bar.getTimePeriod();
            }else {
                result = result.plus(bar.getTimePeriod());
            }
        }
        return result;
    }

    @Override
    public WaveBar<T> update(WaveBar<T> prevBar, T md) {
        WaveBar stroke1 = bars.get(0);
        WaveBar stroke2 = bars.get(1);
        this.begin = DateUtil.min(stroke1.begin, stroke2.begin);
        this.end = DateUtil.max(stroke1.end, stroke2.end);
        switch(direction) {
        case Long:
            this.open = stroke1.getOpenPrice().max(stroke2.getOpenPrice());
            this.close = stroke1.getClosePrice().max(stroke2.getClosePrice());
            break;
        case Short:
            this.open = stroke1.getOpenPrice().min(stroke2.getOpenPrice());
            this.close = stroke1.getClosePrice().min(stroke2.getClosePrice());
            break;
        }
        this.max = open.max(close);
        this.min = open.min(close);
        this.volume = stroke1.getVolume().plus(stroke2.getVolume());
        this.amount = stroke1.getAmount().plus(stroke2.getAmount());

        MarketData mdOpen = getOpenTick(), mdClose = getCloseTick();
        long vol = 0;
        if ( mdOpen!=null ) {
            vol = mdClose.volume-mdOpen.volume;
        }
        if ( vol==0 ) {
            avgPrice = new LongNum(mdClose.averagePrice);
        }else {
            avgPrice = new LongNum( (mdClose.turnover - mdOpen.turnover)/vol );
        }
        openInterest = (mdClose.openInterest);
        mktAvgPrice = new LongNum(mdClose.averagePrice);

        return null;
    }

    @Override
    public Exchangeable getExchangeable() {
        return bars.get(0).getExchangeable();
    }

    @Override
    public boolean canMerge() {
        return false;
    }

    @Override
    public void merge(WaveBar bar) {
        throw new UnsupportedOperationException("merge operation is not supported");
    }

    @Override
    public String toString() {
        Duration dur= DateUtil.between(begin.toLocalDateTime(), end.toLocalDateTime());
        return "CStroke[ "+direction+", B "+DateUtil.date2str(begin.toLocalDateTime())+", "+dur.toSeconds()+"S, O "+open+" C "+close+" H "+max+" L "+min+" ]";
    }

}
