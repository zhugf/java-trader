package trader.service.ta.bar;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.FutureCombo;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.service.md.MarketData;
import trader.service.ta.BarSeriesLoader;
import trader.service.ta.BaseLeveledBarSeries;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.LongNum;

/**
 * 价差套利的BarBuilder
 */
public class FutureComboBarBuilder implements BarBuilder
{
    private FutureCombo futureCombo;
    private ExchangeableTradingTimes tradingTimes;
    private BaseLeveledBarSeries series;
    private PriceLevel level;
    private List<LocalDate> historicalDates = Collections.emptyList();
    private MarketData tick1;
    private MarketData tick2;
    private int tick1BarIdx=-1;
    private int tick2BarIdx=-1;
    private int barIdx=-1;

    public FutureComboBarBuilder(ExchangeableTradingTimes tradingTimes, PriceLevel level) {
        Exchangeable e = tradingTimes.getInstrument();
        if ( e.getType()!=ExchangeableType.FUTURE_COMBO ) {
            throw new RuntimeException("Exchangeable "+e+" is not future combo");
        }
        futureCombo = (FutureCombo)e;
        this.tradingTimes = tradingTimes;
        this.level = level;
        series = new BaseLeveledBarSeries(futureCombo, futureCombo+"-"+level.toString(), level, LongNum::valueOf);
    }

    public PriceLevel getLevel() {
        return level;
    }

    public List<LocalDate> getHistoricalDates(){
        return historicalDates;
    }

    @Override
    public LeveledBarSeries getTimeSeries(PriceLevel level) {
        if ( level==this.level ) {
            return series;
        }
        return null;
    }

    @Override
    public boolean update(MarketData tick) {
        MarketData lastTick1 = tick1, lastTick2 = tick2;
        if ( tick.instrument.equals(futureCombo.getExchangeable1())) {
            tick1 = tick;
            tick1BarIdx = BarSeriesLoader.getBarIndex(tradingTimes, level, tick.updateTime);
        } else if ( tick.instrument.equals(futureCombo.getExchangeable2())){
            tick2 = tick;
            tick2BarIdx = BarSeriesLoader.getBarIndex(tradingTimes, level, tick.updateTime);
        } else {
            //没有匹配的合约
            return false;
        }
        //创建新的BAR
        if ( barIdx<0 && tick1BarIdx>=0 && tick2BarIdx>=0 && tick1BarIdx==tick2BarIdx ) {
            LocalDateTime[] barTimes = BarSeriesLoader.getBarTimes(tradingTimes, level, tick1BarIdx, null);
            FutureComboBar bar = new FutureComboBar(tick1BarIdx, tradingTimes, barTimes[0], tick1, tick2);
            series.addBar(bar);
            barIdx = tick1BarIdx;
            return true;
        }
        if ( barIdx>=0 ) {
            if ( barIdx==tick1BarIdx && barIdx==tick2BarIdx ) {
                ((FutureComboBar)(series.getLastBar())).update(tick1, tick2, null);
                return false;
            }
            if ( barIdx!=tick1BarIdx || barIdx!=tick2BarIdx ) {
                ((FutureComboBar)(series.getLastBar())).update(lastTick1, lastTick2, null);
                barIdx = -1;
                return false;
            }
        }
        return false;
    }

    public void loadHistoryData(BarSeriesLoader seriesLoader) throws IOException
    {
        historicalDates = new ArrayList<>();
        LocalDate startDay = seriesLoader.getStartTradingDay();
        LocalDate endDay = seriesLoader.getEndTradingDay();
        LocalDate day = startDay;
        while(!day.isAfter(endDay)) {
            List<MarketData> ticks1 = seriesLoader.setInstrument(futureCombo.getExchangeable1()).loadMarketDataTicks(day, ExchangeableData.TICK_CTP);
            List<MarketData> ticks2 = seriesLoader.setInstrument(futureCombo.getExchangeable2()).loadMarketDataTicks(day, ExchangeableData.TICK_CTP);
            historicalDates.add(day);
            buildBarsFromTicks(day, ticks1, ticks2);
            day = MarketDayUtil.nextMarketDay(futureCombo.exchange(), day);
        }
    }

    /**
     * 从历史TICK数据构建KBAR
     * @param tradingDay
     * @param ticks1_
     * @param ticks2_
     */
    private void buildBarsFromTicks(LocalDate tradingDay, List<MarketData> ticks1_, List<MarketData> ticks2_) {
        ExchangeableTradingTimes tradingTimes = futureCombo.exchange().getTradingTimes(futureCombo, tradingDay);
        LinkedList<MarketData> ticks1 = new LinkedList<>(ticks1_);
        LinkedList<MarketData> ticks2 = new LinkedList<>(ticks2_);

        int tick1BarIdx=-1;
        int tick2BarIdx=-1;
        LocalDateTime lastTime = DateUtil.min(ticks1.get(0).updateTime, ticks2.get(0).updateTime);
        MarketData tick1=null, tick2=null;
        FutureComboBar bar = null;
        while(ticks1.size()>0 || ticks2.size()>0 ) {
            MarketData tick1_=ticks1.peek(), tick2_=ticks2.peek();
            MarketData lastTick1 = tick1, lastTick2 = tick2;
            boolean pollTick1 = false, pollTick2 = false;
            if ( tick1_!=null && tick2_!=null ) {
                if ( tick1_.updateTime.compareTo(tick2_.updateTime)<=0 ) {
                    tick1 = tick1_;
                    ticks1.poll();
                    pollTick1 = true;
                } else {
                    tick2 = tick2_;
                    ticks2.poll();
                    pollTick2 = true;
                }
            } else {
                if ( tick1_==null&&tick2_!=null ) {
                    tick2 = tick2_;
                    ticks2.poll();
                    pollTick2 = true;
                } else if ( tick1_!=null && tick2_==null ) {
                    tick1 = tick1_;
                    ticks1.poll();
                    pollTick1 = true;
                }
            }
            if ( pollTick1 ) {
                tick1BarIdx = BarSeriesLoader.getBarIndex(tradingTimes, level, tick1.updateTime);
                if( tick1BarIdx<0 ) {
                    tick1 = null;
                }
            }
            if ( pollTick2 ) {
                tick2BarIdx = BarSeriesLoader.getBarIndex(tradingTimes, level, tick2.updateTime);
                if( tick2BarIdx<0 ) {
                    tick2 = null;
                }
            }
            if ( bar==null ) {
                //新增BAR
                if ( tick1!=null && tick2!=null && (tick1BarIdx==tick2BarIdx) ) {
                    LocalDateTime[] barTimes = BarSeriesLoader.getBarTimes(tradingTimes, level, tick1BarIdx, null);

                    bar = new FutureComboBar(tick1BarIdx, tradingTimes, barTimes[0], tick1, tick2);
                    series.addBar(bar);
                }

            } else {
                //E1/E2的时间都更新时，更新当前BAR
                if ( bar.getIndex() == tick1BarIdx && bar.getIndex() == tick2BarIdx && tick1.updateTime.compareTo(tick2.updateTime)==0 ) {
                    bar.update(tick1, tick2, null);
                    continue;
                }
                //E1/E2进入新BAR时，更新现有BAR，并准备创建新BAR
                if ( bar.getIndex() !=tick1BarIdx || bar.getIndex()!=tick2BarIdx ) {
                    bar.update(lastTick1, lastTick2, null);
                    if ( bar.getIndex()==tick1BarIdx ) {
                        tick1BarIdx = -1;
                        tick1 = null;
                    }
                    if ( bar.getIndex()==tick2BarIdx ) {
                        tick2BarIdx = -2;
                        tick2 = null;
                    }
                    bar = null;
                }
            }

        }

    }

}
