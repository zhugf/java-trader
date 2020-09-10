package trader.tool;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVMarshallHelper;
import trader.common.util.CSVUtil;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.ta.BarSeriesLoader;
import trader.service.ta.FutureBarImpl;
import trader.service.util.CmdAction;
import trader.simulator.SimMarketDataService;

public class RepositoryBuildBarAction implements CmdAction {

    private List<String> instrumentFilters = new ArrayList<>();
    private LocalDate beginDate;
    private LocalDate endDate;
    private PrintWriter writer;
    private ExchangeableData data;
    private Map<String, MarketDataProducerFactory> producerFactories;
    private CSVMarshallHelper csvMarshallHelper;
    private MarketDataProducer mdProducer;
    private ThreadPoolExecutor executorService;

    public RepositoryBuildBarAction() {
        executorService = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    @Override
    public String getCommand() {
        return "repository.buildBars";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("repository buildBars [--instruments=e1,e2,e3] [--beginDate=beginDate] [--endDate=endDate]");
        writer.println("\t重新生成行情数据的KBAR数据");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        this.writer = writer;
        parseOptions(options);
        data = TraderHomeUtil.getExchangeableData();
        producerFactories = SimMarketDataService.discoverProducerFactories();
        csvMarshallHelper = createCSVMarshallHelper(MarketDataProducer.PROVIDER_CTP);
        mdProducer = createMarketDataProducer(MarketDataProducer.PROVIDER_CTP);

        for(Exchange exchange:Exchange.getInstances()) {
            for(Exchangeable instrument: data.listHistoryExchangeableIds(exchange)) {
                if ( acceptInstrument(instrument)) {
                    rebuildBars(instrument);
                }
            }
        }
        return 0;
    }

    private void rebuildBars(Exchangeable instrument) throws Exception
    {
        List<LocalDate> tradingDays = data.list(instrument, ExchangeableData.TICK_CTP);

        writer.print(instrument+" : "); writer.flush();
        Collections.sort(tradingDays);
        List<Future<BarInfo>> barInfoFutures = new ArrayList<>();
        for(LocalDate tradingDay:tradingDays) {
            if ( (beginDate!=null && tradingDay.isBefore(beginDate)) || (endDate!=null && tradingDay.isAfter(endDate)) ) {
                continue;
            }
            if ( instrument.exchange().getTradingTimes(instrument, tradingDay)==null) {
                writer.println(" 忽略 "+tradingDay);
                continue;
            }
            String tickCsv = data.load(instrument, ExchangeableData.TICK_CTP, tradingDay);
            Future<BarInfo> barInfoFuture = executorService.submit(()->{
                return loadBar(instrument, tickCsv, tradingDay);
            });
            barInfoFutures.add(barInfoFuture);
        }

        for(Future<BarInfo> barInfoFuture:barInfoFutures) {
            BarInfo barInfo = barInfoFuture.get();
            MarketDataImportAction.saveBars2(data, instrument, ExchangeableData.MIN1, barInfo.tradingDay, barInfo.min1Bars);
            MarketDataImportAction.saveDayBars2(data, instrument, barInfo.tradingDay, barInfo.dayBars);
            writer.print("."); writer.flush();
        }
        RepositoryInstrumentStatsAction.updateInstrumentStats(data, null, instrument, tradingDays);
        writer.println();
    }

    private static class BarInfo{
        public LocalDate tradingDay;
        public List<FutureBarImpl> min1Bars;
        public List<FutureBarImpl> dayBars;
    }

    private BarInfo loadBar(Exchangeable instrument, String tickCsv, LocalDate tradingDay) {
        BarInfo result = new BarInfo();
        result.tradingDay = tradingDay;
        CSVDataSet csvDataSet = CSVUtil.parse(tickCsv);
        List<MarketData> ticks = new ArrayList<>();
        while(csvDataSet.next()) {
            MarketData md = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), tradingDay);
            if ( md!=null ) {
                ticks.add(md);
            }
        }
        if ( !ticks.isEmpty() ) {
            result.min1Bars = BarSeriesLoader.marketDatas2bars(instrument, tradingDay, ExchangeableData.MIN1.getLevel(), ticks);
            result.dayBars = BarSeriesLoader.marketDatas2bars(instrument, tradingDay, ExchangeableData.DAY.getLevel(), ticks);
        }
        return result;
    }

    private boolean acceptInstrument(Exchangeable instrument) {
        if ( instrumentFilters.isEmpty() ) {
            return true;
        }
        for(String f:instrumentFilters) {
            if ( instrument.uniqueId().indexOf(f)>=0 ) {
                return true;
            }
        }
        return false;
    }

    protected void parseOptions(List<KVPair> options) {
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "begindate":
                beginDate = DateUtil.str2localdate(kv.v);
                break;
            case "enddate":
                endDate = DateUtil.str2localdate(kv.v);
                break;
            case "instrument":
                instrumentFilters.add(kv.v);
                break;
            case "instruments":
                for(String p:StringUtil.split(kv.v, ",|;")) {
                    instrumentFilters.add(p);
                }
                break;
            }
        }
        if ( instrumentFilters.isEmpty() ) {
            writer.println("需要指定过滤表达式");
            System.exit(1);
        }
    }

    private MarketDataProducer createMarketDataProducer(String producerType) {
        MarketDataProducerFactory factory = producerFactories.get(producerType);
        if ( factory!=null ) {
            return factory.create(null, Collections.emptyMap());
        }
        return null;
    }

    private CSVMarshallHelper createCSVMarshallHelper(String producerType) {
        MarketDataProducerFactory factory = producerFactories.get(producerType);
        if ( factory!=null ) {
            return factory.createCSVMarshallHelper();
        }
        return null;
    }

}
