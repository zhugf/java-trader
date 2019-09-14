package trader.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVUtil;
import trader.common.util.CSVWriter;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketDataProducerFactory;
import trader.service.util.CmdAction;
import trader.simulator.SimMarketDataService;

/**
 * 对行情数据按日统计, 得到每天每合约的成交量,持仓等数据
 */
public class MarketDataInstrumentStatsAction implements CmdAction{

    private Map<String, MarketDataProducerFactory> producerFactories;
    private PrintWriter writer;
    private ExchangeableData data;
    private LocalDate beginDate;
    private List<String> filters;

    public void setData(ExchangeableData data) {
        this.data = data;
    }

    public void setWriter(PrintWriter writer) {
        this.writer = writer;
    }

    public void setBeginDate(LocalDate beginDate) {
        this.beginDate = beginDate;
    }

    @Override
    public String getCommand() {
        return "marketData.instrumentStats";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("marketData instrumentStats  [--datadir=DATA_DIR] [--contracts=name1,name2] --beginDate=<BEGIN_DATE> --endDate=<END_DATE>");
        writer.println("\t更新合约每日统计数据, 更新每日K线, MIN1K线");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        producerFactories = SimMarketDataService.discoverProducerFactories();
        data = TraderHomeUtil.getExchangeableData();;
        this.writer = writer;
        parseOptions(options);
        //如果没有指定合约,使用所有合约
        for(Exchange exchange:Exchange.getInstances()) {
            for(Exchangeable instrument: data.listHistoryExchangeableIds(exchange)) {
                if ( acceptInstrument(instrument)) {
                    updateInstrumentStats(instrument);
                }
            }
        }
        return 0;
    }

    protected void parseOptions(List<KVPair> options) {
        beginDate = null;
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "filter":
                filters = Arrays.asList(StringUtil.split(kv.v, ",|;"));
                break;
            case "begindate":
                beginDate = DateUtil.str2localdate(kv.v);
                break;
            }
        }

        if ( filters.isEmpty() && beginDate==null ) {
            writer.println("需要提供过滤参数");
            System.exit(1);
        }
    }

    private boolean acceptInstrument(Exchangeable instrument) {
        if ( filters.isEmpty() ) {
            return true;
        }
        for(String f:filters) {
            if ( instrument.uniqueId().indexOf(f)>=0 ) {
                return true;
            }
        }
        return false;
    }

    /**
     * 更新品种的统计数据
     */
    private int updateInstrumentStats(Exchangeable instrument) throws Exception
    {
        int totalDays = 0;
        writer.print(instrument.uniqueId()+" : ");
        //统计合约数据, 从日线数据抓取

        //加载已有统计数据
        String commodity = instrument.commodity();
        TreeSet<LocalDate> existsTradingDays = new TreeSet<>();
        TreeMap<String, String[]> rows = new TreeMap<>();
        if ( data.exists(commodity, ExchangeableData.DAYSTATS, null) ) {
            CSVDataSet csvDataSet = CSVUtil.parse(data.load(commodity, ExchangeableData.DAYSTATS, null));
            while(csvDataSet.next()) {
                String tradingDay = csvDataSet.get(ExchangeableData.COLUMN_TRADINGDAY);
                String key = tradingDay+"-"+csvDataSet.get(ExchangeableData.COLUMN_INSTRUMENT_ID);
                rows.put(key, csvDataSet.getRow() );
                existsTradingDays.add(DateUtil.str2localdate(tradingDay));
            }
        }
        //统计指定日期的合约
        List<LocalDate> tradingDays = new ArrayList<>();
        for(LocalDate tradingDay:data.list(instrument, ExchangeableData.TICK_CTP)) {
            if ( beginDate!=null && tradingDay.isBefore(beginDate)) {
                continue;
            }
            tradingDays.add(tradingDay);
        }
        List<String[]> dayRows = loadInstrumentDayStats(instrument, tradingDays);
        for(String[] row:dayRows) {
            String key = row[1]+"-"+row[0];
            rows.put(key, row);
            totalDays++;
        }
        //保存统计数据
        if ( totalDays!=0 ) {
            CSVWriter csvWriter = new CSVWriter(ExchangeableData.DAYSTATS.getColumns());
            for(String[] row:rows.values()) {
                csvWriter.next();
                csvWriter.setRow(row);
            }
            data.save(commodity, ExchangeableData.DAYSTATS, null, csvWriter.toString());
        }
        writer.println(totalDays);
        return totalDays;
    }

    private List<String[]> loadInstrumentDayStats(Exchangeable instrument, List<LocalDate> tradingDays) throws IOException
    {
        List<String[]> rows = new ArrayList<>();
        CSVDataSet csvDataSet = CSVUtil.parse(data.load(instrument, ExchangeableData.DAY, null));
        while(csvDataSet.next()) {
            LocalDate tradingDay = csvDataSet.getDate(ExchangeableData.COLUMN_DATE);
            if ( !tradingDays.contains(tradingDay)) {
                continue;
            }
            String[] row = new String[ExchangeableData.DAYSTATS.getColumns().length];
            row[0] = instrument.id();
            row[1] = csvDataSet.get(ExchangeableData.COLUMN_DATE);
            row[2] = csvDataSet.get(ExchangeableData.COLUMN_VOLUME);
            row[3] = csvDataSet.get(ExchangeableData.COLUMN_AMOUNT);
            row[4] = csvDataSet.get(ExchangeableData.COLUMN_BEGIN_OPENINT);
            row[5] = csvDataSet.get(ExchangeableData.COLUMN_END_OPENINT);
            rows.add(row);
        }
        return rows;
    }

}
