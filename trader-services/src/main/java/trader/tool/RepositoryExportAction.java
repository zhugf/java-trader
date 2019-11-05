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
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketData;
import trader.service.ta.Bar2;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.TimeSeriesLoader;
import trader.service.util.CmdAction;
import trader.service.util.TimeSeriesHelper;


public class RepositoryExportAction implements CmdAction {

    private Exchangeable instrument;
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
        writer.println("repository export --instrument=INTRUMENT --level=PriceLevel --beginDate=xxx --endDate=yyyy");
        writer.println("\t导出数据");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        parseOptions(options);
        ExchangeableData data = TraderHomeUtil.getExchangeableData();;
        if ( level!=null ) {
            String csv = exportLeveledBars(data);
            FileUtil.save(new File(outputFile), csv);
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
            outputFile = instrument.id()+"-"+this.level+"."+fileExt;
        }
    }

    protected String exportLeveledBars(ExchangeableData data) throws Exception {
        TimeSeriesLoader loader = TimeSeriesHelper.getTimeSeriesLoader().setInstrument(instrument);
        if ( level==PriceLevel.TICKET) {
            CSVWriter csvWriter = new CSVWriter<>(new CtpCSVMarshallHelper());
            LocalDate currDate = beginDate;
            while(currDate.compareTo(endDate)<=0) {
                List<MarketData> ticks = loader.loadMarketDataTicks(currDate, ExchangeableData.TICK_CTP);
                for(MarketData tick:ticks) {
                    csvWriter.next().marshall(tick);
                }
            }
            return csvWriter.toString();
        } else if ( level==PriceLevel.DAY){
            //日线数据忽略起始日期
            String csv = data.load(instrument, ExchangeableData.DAY, null);
            return csv;
        }else {
            //分钟线数据尊重日期
            loader.setLevel(level).setStartTradingDay(beginDate);
            if ( endDate!=null ) {
                loader.setEndTradingDay(endDate);
            }
            LeveledTimeSeries series = loader.load();
            CSVWriter csvWriter = new CSVWriter(
                    "TradingDay",
                    "BeginTime",
                    "EndTime",
                    "Duration",
                    "OpenPrice",
                    "ClosePrice",
                    "MaxPrice",
                    "MinPrice",
                    "AvgPrice",
                    "MktAvgPrice",
                    "Volume",
                    "Amount",
                    "OpenInterest"
                    );
            for(int i=0;i<series.getBarCount();i++) {
                Bar2 bar = (Bar2)series.getBar(i);
                csvWriter.next();
                csvWriter.set("TradingDay", DateUtil.date2str(bar.getTradingTimes().getTradingDay()));
                csvWriter.set("BeginTime", DateUtil.date2str(bar.getBeginTime().toLocalDateTime()));
                csvWriter.set("EndTime", DateUtil.date2str(bar.getEndTime().toLocalDateTime()));
                csvWriter.set("Duration", ""+bar.getTimePeriod().toSeconds());
                csvWriter.set("OpenPrice", bar.getOpenPrice().toString());
                csvWriter.set("ClosePrice", bar.getClosePrice().toString());
                csvWriter.set("MaxPrice", bar.getMaxPrice().toString());
                csvWriter.set("MinPrice", bar.getMinPrice().toString());
                csvWriter.set("AvgPrice", bar.getAvgPrice().toString());
                csvWriter.set("MktAvgPrice", bar.getMktAvgPrice().toString());
                csvWriter.set("Volume", ""+bar.getVolume().getDelegate().intValue());
                csvWriter.set("OpenInterest", ""+bar.getOpenInterest());
                csvWriter.set("Amount", bar.getAmount().toString());
            }
            return csvWriter.toString();
        }
    }

}
