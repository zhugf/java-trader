package trader.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import trader.service.util.CmdAction;
import trader.simulator.SimMarketDataService;

public class RepositoryBuildBarAction implements CmdAction {

    private List<String> instrumentFilters = new ArrayList<>();
    private LocalDate beginDate;
    private PrintWriter writer;
    private ExchangeableData data;
    private Map<String, MarketDataProducerFactory> producerFactories;

    public RepositoryBuildBarAction() {

    }

    @Override
    public String getCommand() {
        return "repository.buildBars";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("repository buildBars [--instruments=e1,e2,e3] [--beginDate=beginDate]");
        writer.println("\t重新生成行情数据的KBAR数据");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        this.writer = writer;
        parseOptions(options);
        data = TraderHomeUtil.getExchangeableData();
        producerFactories = SimMarketDataService.discoverProducerFactories();

        for(Exchange exchange:Exchange.getInstances()) {
            for(Exchangeable instrument: data.listHistoryExchangeableIds(exchange)) {
                if ( acceptInstrument(instrument)) {
                    rebuildBars(instrument);
                }
            }
        }
        return 0;
    }

    private void rebuildBars(Exchangeable instrument) throws IOException {
        List<LocalDate> tradingDays = data.list(instrument, ExchangeableData.TICK_CTP);

        CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(MarketDataProducer.PROVIDER_CTP);
        MarketDataProducer mdProducer = createMarketDataProducer(MarketDataProducer.PROVIDER_CTP);
        writer.print(instrument+" : ");
        Collections.sort(tradingDays);
        for(LocalDate tradingDay:tradingDays) {
            if ( beginDate!=null && tradingDay.isBefore(beginDate) ) {
                continue;
            }
            String tickCsv = data.load(instrument, ExchangeableData.TICK_CTP, tradingDay);
            CSVDataSet csvDataSet = CSVUtil.parse(tickCsv);
            List<MarketData> ticks = new ArrayList<>();
            while(csvDataSet.next()) {
                MarketData md = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), tradingDay);
                if ( md!=null ) {
                    ticks.add(md);
                }
            }
            if ( !ticks.isEmpty() ) {
                MarketDataImportAction.saveMin1Bars(data, instrument, tradingDay, ticks);
                MarketDataImportAction.saveDayBars(data, instrument, tradingDay, ticks);
                writer.print("."); writer.flush();
            }
        }
        RepositoryInstrumentStatsAction.updateInstrumentStats(data, writer, instrument, tradingDays);
        writer.println();
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
