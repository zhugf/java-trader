package trader.service.util;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.ta4j.core.indicators.SMAIndicator;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.IniFile;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.common.util.StringUtil.KVPair;
import trader.service.ta.BarSeriesLoader;
import trader.service.ta.FutureBar;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.indicators.BeginOpenIntIndicator;
import trader.service.ta.indicators.DayVolumeIndicator;

public abstract class AbsCmdAction implements CmdAction {

    protected ExchangeableData data = TraderHomeUtil.getExchangeableData();
    protected Exchangeable instrument = null;
    protected LocalDate beginDate;
    protected LocalDate endDate;
    protected PriceLevel level = null;
    protected String outputFile;

    protected BeansContainer beansContainer;
    protected PrintWriter writer;
    protected BarSeriesLoader loader;

    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        this.writer = writer;
        this.loader = new BarSeriesLoader(beansContainer, data);
        this.beansContainer = beansContainer;
        return executeImpl(options);
    }

    public abstract int executeImpl(List<KVPair> options) throws Exception;

    protected boolean hasBatchOptions(List<KVPair> options) {
        for(KVPair kv:options) {
            if ( kv.k.toLowerCase().equals("batchfile")) {
                return true;
            }
        }
        return false;
    }

    protected List<List<KVPair>> parseBatchOptions(List<KVPair> options) throws Exception
    {
        List<List<KVPair>> result = new ArrayList<>();
        if ( !hasBatchOptions(options) ) {
            result.add(options);
        } else {
            String batchFile = null;
            for(Iterator<KVPair> it=options.iterator(); it.hasNext();) {
                KVPair kv = it.next();
                if ( kv.k.toLowerCase().equals("batchfile")) {
                    it.remove();
                    batchFile = kv.v;
                }
            }
            //读取Batch文件, 为每个Section创建一个Options列表
            for(IniFile.Section section: (new IniFile(new File(batchFile))).getAllSections()) {
                Map<String, KVPair> options0 = new TreeMap<>();
                Properties props = section.getProperties();
                for(KVPair kv:options) {
                    options0.put(kv.k, kv);
                }
                for(Object key:props.keySet()) {
                    String k = key.toString(), v = props.getProperty(k);
                    options0.put(k, new KVPair(k,v, k+"="+v));
                }
                result.add(new ArrayList<>(options0.values()));
            }
        }
        return result;
    }

    protected void parseOptions(List<KVPair> options) {
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "instrument":
                instrument = Exchangeable.fromString(kv.v);
            case "begindate":
                beginDate = DateUtil.str2localdate(kv.v);
                break;
            case "enddate":
                endDate = DateUtil.str2localdate(kv.v);
                break;
            case "level":
                level = PriceLevel.valueOf(kv.v.toLowerCase());
                break;
            case "outputfile":
                this.outputFile = kv.v;
                break;
            }
        }
        if (beginDate==null) {
            beginDate = MarketDayUtil.lastMarketDay(Exchange.SHFE, false);
        }
        if (endDate==null) {
            endDate = MarketDayUtil.lastMarketDay(Exchange.SHFE, false);
        }
        if ( outputFile==null) {
            outputFile = instrument+"-"+this.level+".csv";
        }
    }

    protected LeveledBarSeries daySeries;
    protected Map<LocalDate, FutureBar> dayBars = null;
    /**
     * 经过单边双边调整后的值
     */
    protected Map<LocalDate, Long> volDayVolumeValues = null;
    /**
     * 经过单边双边调整后的值
     */
    protected Map<LocalDate, Long> volDayOpenIntValues = null;

    /**
     * 分析实际的交易日量K线值
     */
    protected PriceLevel resolveVolumeLevel(BarSeriesLoader loader, LocalDate tradingDay) throws Exception
    {
        if ( level.postfixes().isEmpty() ) {
            //量K线直接给出绝对值
            return level;
        }
        //量K线基于百分比
        String percentBy = level.postfixes().get(PriceLevel.POSTFIX_PERCENT);
        String basedBy = level.postfixes().get(PriceLevel.POSTFIX_BASE);
        int dayCount = 1;
        if ( level.postfixes().containsKey(PriceLevel.POSTFIX_DAY)) {
            dayCount = ConversionUtil.toInt(level.postfixes().get(PriceLevel.POSTFIX_DAY));
        }
        if ( null==volDayVolumeValues ) {
            daySeries = loader.setLevel(PriceLevel.DAY).load();
            DayVolumeIndicator volIndicator = new DayVolumeIndicator(daySeries);
            BeginOpenIntIndicator openIntIndicator = new BeginOpenIntIndicator(daySeries);
            SMAIndicator volSMAIndicator = new SMAIndicator(volIndicator, dayCount);
            SMAIndicator openIntSMAIndicator = new SMAIndicator(openIntIndicator, dayCount);
            volDayVolumeValues = new TreeMap<>();
            volDayOpenIntValues = new TreeMap<>();
            dayBars = new TreeMap<>();
            for(int i=0;i<daySeries.getBarCount();i++) {
                LocalDate barDay = daySeries.getBar2(i).getTradingTimes().getTradingDay();
                volDayVolumeValues.put(barDay, volSMAIndicator.getValue(i).longValue());
                volDayOpenIntValues.put(barDay, openIntSMAIndicator.getValue(i).longValue());
                dayBars.put(barDay, daySeries.getBar2(i));
            }
        }
        if ( null!=percentBy) {
            //使用过去N天的volume的/openInt的均值除以value
            Long volValue = null;
            if (StringUtil.equals(percentBy, PriceLevel.BY_VOL)) {
                volValue = volDayVolumeValues.get(tradingDay);
            } else if (StringUtil.equals(percentBy, PriceLevel.BY_OPENINT)) {
                volValue = volDayOpenIntValues.get(tradingDay);
            }
            if ( null==volValue) {
                throw new Exception("Price level "+this.level+" has no data on "+tradingDay+" failed");
            }
            //恢复原始单边双边
            volValue = instrument.exchange().adjustOpenInt(instrument, tradingDay, volValue, true);
            return PriceLevel.valueOf(PriceLevel.LEVEL_VOL+(volValue/level.value()));
        }
        if ( null!=basedBy ) {
            //使用过去N天的每日openInt与Volume比值的均值
            //每天的比值的计算公式: (Volume-|beginOpenInt-endOpenInt|)/Max(beginOpenInt, endOpenInt)
            if (StringUtil.equals(basedBy, PriceLevel.BY_OPENINT)) {
                int dayIdx = 0;
                FutureBar dayBar = null;
                for(int i=0;i<daySeries.getBarCount();i++) {
                    FutureBar bar = daySeries.getBar2(i);
                    if ( tradingDay.equals(bar.getTradingTimes().getTradingDay()) ){
                        dayIdx = i;
                        dayBar = bar;
                        break;
                    }
                }
                double sum = 0;
                int barCount = 0;
                for (int i = Math.max(0, dayIdx - dayCount + 1); i <= dayIdx; i++) {
                    FutureBar bar = daySeries.getBar2(i);
                    long openIntChange = Math.abs(bar.getBeginOpenInt()-bar.getEndOpenInt());
                    double d1 = (bar.getVolume().longValue()-openIntChange);
                    double d2 = Math.max(bar.getBeginOpenInt(), bar.getEndOpenInt());
                    sum += d1/d2;
                    barCount++;
                }
                double avg = sum/barCount;
                double volPredict = avg*dayBar.getBeginOpenInt();
                int levelValue = (int)(volPredict/level.value());
                return PriceLevel.valueOf(PriceLevel.LEVEL_VOL+levelValue);
            }
        }
        throw new RuntimeException("Unsupported level: "+this.level);
    }

    protected File getDailyFile(LocalDate tradingDay) {
        File file = null;
        if ( level==PriceLevel.DAY || null==tradingDay) {
            file = new File(instrument.uniqueId()+"_"+this.level+".csv");
        } else {
            file = new File(instrument.uniqueId()+"_"+DateUtil.date2str(tradingDay)+"_"+this.level+".csv");
        }
        return file;
    }
}
