package trader.service.util;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Properties;
import java.util.TreeMap;

import org.ta4j.core.indicators.SMAIndicator;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.Future;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVUtil;
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
        if ( hasBatchOptions(options)) {
            return executeImpl(options);
        } else {
            for(List<KVPair> opts:parseBatchOptions(options)) {
                executeImpl(opts);
            }
            return 0;
        }
    }

    public abstract int executeImpl(List<KVPair> options) throws Exception;

    private boolean hasBatchOptions(List<KVPair> options) {
        for(KVPair kv:options) {
            if ( kv.k.toLowerCase().equals("batchfile")) {
                return true;
            }
        }
        return false;
    }

    private List<List<KVPair>> parseBatchOptions(List<KVPair> options) throws Exception
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
            outputFile = getDefaultOutputFile();
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
        String primary = level.postfixes().get(PriceLevel.POSTFIX_PRIMARY);
        int dayCount = 1;
        if ( level.postfixes().containsKey(PriceLevel.POSTFIX_DAY)) {
            dayCount = ConversionUtil.toInt(level.postfixes().get(PriceLevel.POSTFIX_DAY));
        }
        if ( null==volDayVolumeValues ) {
            if (StringUtil.isEmpty(primary)) {
                //使用指定合约
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
            } else {
                //使用主连合约
                Future future = (Future)instrument;
                Exchangeable contract = Exchangeable.fromString(future.contract());
                String dayStatsCsv = this.data.load(contract, ExchangeableData.DAYSTATS, null);
                TreeMap<LocalDate, DayStats> primaryInstruments = buildDayStats(dayStatsCsv);
                volDayVolumeValues = new TreeMap<>();
                volDayOpenIntValues = new TreeMap<>();
                for(LocalDate date0:primaryInstruments.keySet()) {
                    DayStats dayStats = primaryInstruments.get(date0);
                    volDayVolumeValues.put(date0, dayStats.pri_vol);
                    volDayOpenIntValues.put(date0, dayStats.pri_oi);
                }
            }
        }
        {
            //计算 volume 移动均值
            LinkedList<Long> recentVols = new LinkedList<>();
            for(LocalDate date:volDayVolumeValues.keySet()) {
                Long vol = volDayVolumeValues.get(date);
                if (date.equals(tradingDay)) {
                    break;
                }
                recentVols.offer(vol);
                if ( recentVols.size()>dayCount) {
                    recentVols.poll();
                }
            }
            OptionalDouble volAvg = recentVols.stream().mapToLong(Long::longValue).average();
            int level0 = (int)(volAvg.getAsDouble()/level.value());
            return PriceLevel.valueOf(PriceLevel.LEVEL_VOL+level0);
        }
        //throw new RuntimeException("Unsupported level: "+this.level);
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

    protected String getDefaultOutputFile() {
        return instrument+"-"+this.level+".csv";
    }

    /**
     * 期货品种每日统计
     */
    private static class DayStats{
        long pri_vol;
        long pri_oi;
        String pri_instrument;

        long sec_vol;
        long sec_oi;
        String sec_instrument;

        public void merge(String instrument, long volume, long oi) {
            if ( oi>pri_oi) {
                if ( pri_oi!=0 && pri_oi>sec_oi ) {
                    sec_instrument = pri_instrument;
                    sec_vol = pri_vol;
                    sec_oi = pri_oi;
                }
                pri_instrument = instrument;
                pri_vol = volume;
                pri_oi = oi;
            }else if ( oi>sec_oi) {
                sec_instrument = instrument;
                sec_vol = volume;
                sec_oi = oi;
            }
        }
    }

    private TreeMap<LocalDate, DayStats> buildDayStats(String csvText) {
        //首先找到每日主力
        TreeMap<LocalDate, DayStats> primaryInstruments = new TreeMap<>();
        CSVDataSet csv = CSVUtil.parse(csvText);
        while(csv.next()) {
            LocalDate day = csv.getDate("TradingDay");
            //为了实现滚动均值计算, 特意忽略beginDate
//            if ( beginDate!=null && day.isBefore(beginDate)) {
//                continue;
//            }
            if ( endDate!=null && day.isAfter(endDate)) {
                continue;
            }
            String instrument = csv.get("InstrumentId");
            if ( Exchangeable.fromString(instrument).getType()!=ExchangeableType.FUTURE) {
                continue;
            }
            DayStats dayStats0 = primaryInstruments.get(day);
            if (null==dayStats0) {
                dayStats0 = new DayStats();
                primaryInstruments.put(day, dayStats0);
            }
            dayStats0.merge(instrument, csv.getLong("Volume"), csv.getLong("EndOpenInt"));
        }
        return primaryInstruments;
    }

}
