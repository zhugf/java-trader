package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.ta4j.core.indicators.SMAIndicator;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.CSVWriter;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.service.ta.BarSeriesLoader;
import trader.service.ta.FutureBar;
import trader.service.ta.FutureBarImpl;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.indicators.BeginOpenIntIndicator;
import trader.service.ta.indicators.DayVolumeIndicator;
import trader.service.util.AbsCmdAction;
import trader.service.util.TimeSeriesHelper;


public class RepositoryExportKBarAction extends AbsCmdAction {

    private boolean filePerDay;

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
    public int executeImpl(List<KVPair> options) throws Exception
    {
        parseOptions(options);
        List<LeveledBarSeries> allDaySeries = loadBars();
        if ( allDaySeries.size()>0 ) {
            saveBar(allDaySeries);
        }
        return 0;
    }

    protected void parseOptions(List<KVPair> options) {
        super.parseOptions(options);
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "fileperday":
                filePerDay = ConversionUtil.toBoolean(kv.v);
                break;
            }
        }
        if ( !filePerDay && outputFile==null) {
            outputFile = instrument.uniqueId()+"_"+this.level+".csv";
        }
        if ( level==PriceLevel.DAY ) {
            filePerDay = false;
        }
    }

    /**
     * 按天加载KBar
     * @return
     * @throws Exception
     */
    protected List<LeveledBarSeries> loadBars() throws Exception
    {
        loader.setInstrument(instrument);
        List<LeveledBarSeries> result = new ArrayList<>();
        if ( level==PriceLevel.DAY ) {
            String csv = data.load(instrument, ExchangeableData.DAY, null);
            FileUtil.save(new File(outputFile), csv);
            writer.println("导出 "+instrument+" KBAR数据文件: "+outputFile);
        } else if (StringUtil.equals(level.prefix(), PriceLevel.LEVEL_MIN)) {
            //分钟线
            LeveledBarSeries daySeries = loader.setLevel(level).setStartTradingDay(beginDate).setEndTradingDay(endDate).load();
            result.add(daySeries);
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

    protected void saveBar(List<LeveledBarSeries> allDaySeries) throws Exception
    {
        if ( filePerDay ) {
            LocalDate currDay = null;
            CSVWriter csvWriter = null;
            int bar0Idx = -1;
            for(LeveledBarSeries series:allDaySeries) {
                for(int i=0;i<series.getBarCount();i++) {
                    FutureBar bar = series.getBar2(i);
                    LocalDate tradingDay = bar.getTradingTimes().getTradingDay();
                    int barIndex = bar.getIndex();
                    if ( !tradingDay.equals(currDay)) {
                        if ( null!=csvWriter ) {
                            File file = getDailyFile(currDay);
                            FileUtil.save(file, csvWriter.toString());
                            writer.println((bar0Idx!=0?"(数据异常)":"")+"导出 "+instrument+" "+currDay+" KBAR文件: "+file);
                        }
                        currDay = tradingDay;
                        bar0Idx = barIndex;
                        csvWriter = new CSVWriter(ExchangeableData.FUTURE_MIN_COLUMNS);
                    }
                    csvWriter.next();
                    ((FutureBarImpl)bar).save(csvWriter);
                }
            }
            if ( null!=csvWriter ) {
                File file = getDailyFile(currDay);
                FileUtil.save(file, csvWriter.toString());
                writer.println((bar0Idx!=0?"(数据异常)":"")+"导出 "+instrument+" "+currDay+" KBAR文件: "+file);
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

}
