package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
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
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketData;
import trader.service.ta.FutureBar;
import trader.service.ta.FutureBarImpl;
import trader.service.ta.LeveledBarSeries;
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
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        this.writer = writer;
        parseOptions(options);
        ExchangeableData data = TraderHomeUtil.getExchangeableData();;
        if ( level!=null ) {
            if ( !filePerDay ) {
                exportLeveledBars(data);
            } else {
                exportLeveledBars2(data);
            }
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
    }

    protected void exportLeveledBars(ExchangeableData data) throws Exception {
        String csv = null;
        BarSeriesLoader loader = TimeSeriesHelper.getTimeSeriesLoader().setInstrument(instrument);
        if ( level==PriceLevel.TICKET) {
            CSVWriter csvWriter = new CSVWriter<>(new CtpCSVMarshallHelper());
            LocalDate currDate = beginDate;
            while(currDate.compareTo(endDate)<=0) {
                List<MarketData> ticks = loader.loadMarketDataTicks(currDate, ExchangeableData.TICK_CTP);
                for(MarketData tick:ticks) {
                    csvWriter.next().marshall(tick);
                }
            }
            csv = csvWriter.toString();
        } else if ( level==PriceLevel.DAY){
            //日线数据忽略起始日期
            csv = data.load(instrument, ExchangeableData.DAY, null);
        }else {
            //分钟线数据尊重日期
            loader.setLevel(level).setStartTradingDay(beginDate);
            if ( endDate!=null ) {
                loader.setEndTradingDay(endDate);
            }
            LeveledBarSeries series = loader.load();
            CSVWriter csvWriter = new CSVWriter(ExchangeableData.FUTURE_MIN_COLUMNS);

            for(int i=0;i<series.getBarCount();i++) {
                FutureBar bar = (FutureBar)series.getBar(i);
                csvWriter.next();
                ((FutureBarImpl)bar).save(csvWriter);
            }
            csv = csvWriter.toString();
        }
        FileUtil.save(new File(outputFile), csv);
        writer.println("导出 "+instrument+" KBAR数据文件: "+outputFile);
    }

    /**
     * 按交易日导出文件
     */
    protected void exportLeveledBars2(ExchangeableData data) throws Exception {
        BarSeriesLoader loader = TimeSeriesHelper.getTimeSeriesLoader().setInstrument(instrument);
        if ( level==PriceLevel.TICKET) {
            LocalDate currDate = beginDate;
            while(currDate.compareTo(endDate)<=0) {
                List<MarketData> ticks = loader.loadMarketDataTicks(currDate, ExchangeableData.TICK_CTP);
                CSVWriter csvWriter = new CSVWriter<>(new CtpCSVMarshallHelper());
                for(MarketData tick:ticks) {
                    csvWriter.next().marshall(tick);
                }
                File file = getDailyFile(currDate);
                FileUtil.save(file, csvWriter.toString());
                writer.println("导出 "+instrument+" "+currDate+" TICK文件: "+file);
            }
        } else if ( level==PriceLevel.DAY){
            //日线数据忽略起始日期
            String csv = data.load(instrument, ExchangeableData.DAY, null);
            File file = getDailyFile(null);
            FileUtil.save(file, csv);
            writer.println("导出 "+instrument+" KBAR数据文件: "+file);
        }else {
            //分钟线数据尊重日期
            loader.setLevel(level).setStartTradingDay(beginDate);
            if ( endDate!=null ) {
                loader.setEndTradingDay(endDate);
            }
            LeveledBarSeries series = loader.load();

            CSVWriter csvWriter = null;
            LocalDate tradingDay = null;
            for(int i=0;i<series.getBarCount();i++) {
                FutureBar bar = (FutureBar)series.getBar(i);
                if ( bar.getIndex()==0 ) {
                    if ( csvWriter!=null ) {
                        File file = getDailyFile(tradingDay);
                        FileUtil.save(file, csvWriter.toString());
                        writer.println("导出 "+instrument+" "+tradingDay+" KBAR文件: "+file);
                    }
                    csvWriter = new CSVWriter(ExchangeableData.FUTURE_MIN_COLUMNS);
                    tradingDay = bar.getTradingTimes().getTradingDay();
                }
                csvWriter.next();
                ((FutureBarImpl)bar).save(csvWriter);
            }
            File file = getDailyFile(tradingDay);
            FileUtil.save(file, csvWriter.toString());
            writer.println("导出 "+instrument+" "+tradingDay+" KBAR文件: "+file);
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
