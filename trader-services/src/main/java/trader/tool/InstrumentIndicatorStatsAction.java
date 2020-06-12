package trader.tool;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.service.ta.BarSeriesLoader;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.indicators.ATRIndicator;
import trader.service.ta.indicators.MACDIndicator;
import trader.service.util.CmdAction;

@Discoverable(interfaceClass = CmdAction.class, purpose = "InstrumentIndicatorStatsAction")
public class InstrumentIndicatorStatsAction implements CmdAction {
    protected Exchangeable instrument;
    protected LocalDate beginDate;
    protected LocalDate endDate;
    /**
     * level 参数
     */
    protected String level = null;
    protected ExchangeableData data;
    protected BarSeriesLoader loader;

    @Override
    public String getCommand() {
        return "instrument.indicatorStats";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("instrument indicatorStats --instrument=<EXCHANGEABLE> --level=min1/min3/min5/min15/vol1}k/vol2k/volDaily --beginDate=<BEGIN_DATE> --endDate=<END_DATE>");
        writer.println("\t统计品种在某个Levle的MACD均值");
    }
    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        parseOptions(options);
        data = TraderHomeUtil.getExchangeableData();
        loader = new BarSeriesLoader(beansContainer, data);
        loader.setInstrument(instrument)
            .setStartTradingDay(beginDate)
            .setEndTradingDay(endDate)
            .setLevel(PriceLevel.valueOf(level));

        LeveledBarSeries series = loader.setStartTradingDay(beginDate).setEndTradingDay(endDate).load();
        ClosePriceIndicator closePrices = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrices);
        ATRIndicator atr = new ATRIndicator(series, 30);
        List<Num> atrVals = new ArrayList<>(series.getBarCount());
        List<Num> diffVals = new ArrayList<>(series.getBarCount());
        List<Num> deaVals = new ArrayList<>(series.getBarCount());
        for(int i=30; i<series.getBarCount();i++) {
            atrVals.add(atr.getValue(i).abs());
            diffVals.add(macd.getDIFF().getValue(i).abs());
            deaVals.add(macd.getDEA().getValue(i).abs());
        }
        Num zero = series.numOf(0);
        Num atrSum = zero, diffSum = zero, deaSum = zero;
        for(int i=0;i<atrVals.size();i++) {
            atrSum = atrSum.plus(atrVals.get(i));
            diffSum = diffSum.plus(diffVals.get(i));
            deaSum = deaSum.plus(deaVals.get(i));
        }
        Num atrAvg = atrSum.dividedBy(series.numOf(atrVals.size()));
        Num diffAvg = diffSum.dividedBy(series.numOf(atrVals.size()));
        Num deaAvg = deaSum.dividedBy(series.numOf(atrVals.size()));

        Collections.sort(atrVals);
        Collections.sort(diffVals);
        Collections.sort(deaVals);
        int p95 = (int)(atrVals.size()*0.95);
        Num atrSum0 = zero, diffSum0 = zero, deaSum0 = zero;
        for(int i=0;i<p95;i++) {
            atrSum0 = atrSum0.plus(atrVals.get(i));
            diffSum0 = diffSum0.plus(diffVals.get(i));
            deaSum0 = deaSum0.plus(deaVals.get(i));
        }
        Num atrAvg0 = atrSum0.dividedBy(series.numOf(p95));
        Num diffAvg0 = diffSum0.dividedBy(series.numOf(p95));
        Num deaAvg0 = deaSum0.dividedBy(series.numOf(p95));

        writer.println(instrument+" "+beginDate+"-"+endDate+" load bars: "+series.getBarCount());
        writer.println("Average:");
        writer.println("\tATR: "+atrAvg+" 95% "+atrAvg0);
        writer.println("\tMACD.DIFF: "+diffAvg+" 95% "+diffAvg0);
        writer.println("\tMACD.DEA: "+deaAvg+" 95% "+deaAvg0);

        return 0;
    }

    protected void parseOptions(List<KVPair> options) {
        instrument = null;
        beginDate = null;
        endDate = null;
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "instrument":
                instrument = Exchangeable.fromString(kv.v);
                break;
            case "begindate":
                beginDate = DateUtil.str2localdate(kv.v);
                break;
            case "enddate":
                endDate = DateUtil.str2localdate(kv.v);
                break;
            case "level":
                this.level = kv.v.toLowerCase();
                break;
            }
        }

        if (beginDate==null) {
            beginDate = MarketDayUtil.lastMarketDay(Exchange.SHFE, false);
        }
        if (endDate==null) {
            endDate = MarketDayUtil.lastMarketDay(Exchange.SHFE, false);
        }
    }

}
