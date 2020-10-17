package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVUtil;
import trader.common.util.CSVWriter;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketData;
import trader.service.ta.FutureBar;
import trader.service.ta.FutureBarImpl;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.indicators.BeginOpenIntIndicator;
import trader.service.ta.indicators.OpenIntIndicator;
import trader.service.ta.BarSeriesLoader;
import trader.service.util.CmdAction;
import trader.service.util.TimeSeriesHelper;


public class RepositoryExportKBarAction implements CmdAction {

    private PrintWriter writer;
    private Exchangeable instrument;
    private boolean filePerDay;
    private LocalDate beginDate;
    private LocalDate endDate;
    private PriceLevel level;
    private String outputFile;

    @Override
    public String getCommand() {
        return "repository.export";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("repository export --instrument=INTRUMENT --level=PriceLevel --filePerDay=true --beginDate=xxx --endDate=yyyy");
        writer.println("\t导出KBAR数据");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        this.writer = writer;
        parseOptions(options);
        List<LeveledBarSeries> allDaySeries = loadBars();
        if ( allDaySeries.size()>0 ) {
            saveBar(allDaySeries);
        }
        return 0;
    }

    protected void parseOptions(List<KVPair> options) {
        instrument = null;
        beginDate = null;
        endDate = null;
        outputFile = null;
        String fileExt = "csv";
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
                level = PriceLevel.valueOf(kv.v.toLowerCase());
                break;
            case "fileperday":
                filePerDay = ConversionUtil.toBoolean(kv.v);
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
        if ( !filePerDay && outputFile==null) {
            outputFile = instrument.uniqueId()+"_"+this.level+"."+fileExt;
        }
        if ( level==PriceLevel.DAY ) {
            filePerDay = false;
        }
    }

    protected List<LeveledBarSeries> loadBars() throws Exception
    {
        ExchangeableData data = TraderHomeUtil.getExchangeableData();;
        BarSeriesLoader loader = TimeSeriesHelper.getTimeSeriesLoader().setInstrument(instrument);
        List<LeveledBarSeries> result = new ArrayList<>();
        if ( level==PriceLevel.DAY ) {
            String csv = data.load(instrument, ExchangeableData.DAY, null);
            FileUtil.save(new File(outputFile), csv);
            writer.println("导出 "+instrument+" KBAR数据文件: "+outputFile);
        } else if (StringUtil.equals(level.prefix(), PriceLevel.LEVEL_MIN)) {
            //分钟线
            if ( filePerDay ) {
                LocalDate tradingDay = beginDate;
                while(tradingDay.compareTo(endDate)<=0) {
                    LeveledBarSeries daySeries = loader.setLevel(level).setStartTradingDay(tradingDay).setEndTradingDay(tradingDay).load();
                    result.add(daySeries);
                }
            } else {
                LeveledBarSeries daySeries = loader.setLevel(level).setStartTradingDay(beginDate).setEndTradingDay(endDate).load();
                result.add(daySeries);
            }
        } else if ( StringUtil.equals(level.prefix(), PriceLevel.LEVEL_VOL)) {
            //量K线
            LocalDate tradingDay = beginDate;
            while(tradingDay.compareTo(endDate)<=0) {
                PriceLevel dayLevel = resolveVolumeLevel(loader, tradingDay);
                FutureBar dayBar = dayBars.get(tradingDay);
                LeveledBarSeries daySeries = loader.setLevel(dayLevel).setStartTradingDay(tradingDay).setEndTradingDay(tradingDay).load();

                writer.println(instrument+" "+DateUtil.date2str(tradingDay)+" level "+dayLevel+" bars: "+daySeries.getBarCount()+" vol "+dayBar.getVolume()+" openInt "+dayBar.getBeginOpenInt()+"/"+dayBar.getEndOpenInt());
                result.add(daySeries);
                tradingDay = MarketDayUtil.nextMarketDay(instrument.exchange(), tradingDay);
            }
        }
        return result;
    }

    private Map<LocalDate, FutureBar> dayBars = null;
    private Map<LocalDate, Integer> volLevelDayValues = null;
    /**
     * 分析实际的交易日量K线值
     */
    private PriceLevel resolveVolumeLevel(BarSeriesLoader loader, LocalDate tradingDay) throws Exception
    {
        String percentBy = level.postfixes().get(PriceLevel.POSTFIX_PERCENT);
        if ( null==percentBy ) {
            //量K线直接给出绝对值
            return level;
        }
        //量K线基于百分比
        if ( null==volLevelDayValues ) {
            int day = 1;
            if ( level.postfixes().containsKey(PriceLevel.POSTFIX_DAY)) {
                day = ConversionUtil.toInt(level.postfixes().get(PriceLevel.POSTFIX_DAY));
            }
            LeveledBarSeries daySeries = loader.setLevel(PriceLevel.DAY).load();
            VolumeIndicator volIndicator = new VolumeIndicator(daySeries);
            BeginOpenIntIndicator openIntIndicator = new BeginOpenIntIndicator(daySeries);
            SMAIndicator smaIndicator = null;
            if ( StringUtil.equals(percentBy, PriceLevel.PERCENT_VOL)) {
                smaIndicator = new SMAIndicator(volIndicator, day);
            } else if ( StringUtil.equals(percentBy, PriceLevel.PERCENT_OPENINT)) {
                smaIndicator = new SMAIndicator(openIntIndicator, day);
            } else {
                throw new Exception("Unsupported percent by "+percentBy+" in level "+level);
            }
            volLevelDayValues = new TreeMap<>();
            dayBars = new TreeMap<>();
            for(int i=0;i<daySeries.getBarCount();i++) {
                LocalDate barDay = daySeries.getBar2(i).getTradingTimes().getTradingDay();
                volLevelDayValues.put(barDay, smaIndicator.getValue(i).intValue());
                dayBars.put(barDay, daySeries.getBar2(i));
            }
        }
        Integer volValue = volLevelDayValues.get(tradingDay);
        if ( null==volValue) {
            throw new Exception("Price level "+this.level+" has no data on "+tradingDay+" failed");
        }
        return PriceLevel.valueOf(PriceLevel.LEVEL_VOL+(volValue/level.value()));
    }

    protected void saveBar(List<LeveledBarSeries> allDaySeries) throws Exception
    {
        if ( filePerDay ) {
            for(LeveledBarSeries series:allDaySeries) {
                LocalDate tradingDay = series.getBar2(0).getTradingTimes().getTradingDay();
                int bar0Index = series.getBar2(0).getIndex();
                CSVWriter csvWriter = new CSVWriter(ExchangeableData.FUTURE_MIN_COLUMNS);
                for(int i=0;i<series.getBarCount();i++) {
                    csvWriter.next();
                    FutureBar bar = series.getBar2(i);
                    ((FutureBarImpl)bar).save(csvWriter);
                }
                File file = getDailyFile(tradingDay);
                FileUtil.save(file, csvWriter.toString());
                writer.println((bar0Index!=0?"(数据异常)":"")+"导出 "+instrument+" "+tradingDay+" KBAR文件: "+file);
            }
        } else {
            CSVWriter csvWriter = new CSVWriter(ExchangeableData.FUTURE_MIN_COLUMNS);
            for(LeveledBarSeries series:allDaySeries) {
                for(int i=0;i<series.getBarCount();i++) {
                    csvWriter.next();
                    FutureBar bar = series.getBar2(i);
                    ((FutureBarImpl)bar).save(csvWriter);
                }
            }
            FileUtil.save(new File(outputFile), csvWriter.toString());
            writer.println("导出 "+instrument+" KBAR数据文件: "+outputFile);
        }
    }

    private File getDailyFile(LocalDate tradingDay) {
        File file = null;
        if ( level==PriceLevel.DAY ) {
            file = new File(instrument.uniqueId()+"_"+this.level+".csv");
        } else {
            file = new File(instrument.uniqueId()+"_"+DateUtil.date2str(tradingDay)+"_"+this.level+".csv");
        }
        return file;
    }

}
