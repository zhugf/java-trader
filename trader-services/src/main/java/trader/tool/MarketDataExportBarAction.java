package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.StochasticOscillatorDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.StochasticRSIIndicator;
import org.ta4j.core.indicators.WilliamsRIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.bollinger.PercentBIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.CSVWriter;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.IniFile;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.service.ta.Bar2;
import trader.service.ta.BarSeriesLoader;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.indicators.BIASIndicator;
import trader.service.ta.indicators.KDJIndicator;
import trader.service.ta.indicators.MACDIndicator;
import trader.service.ta.indicators.PUBUIndicator;
import trader.service.util.CmdAction;

/**
 * 导出某个品种的数据特征集合
 */
@Discoverable(interfaceClass = CmdAction.class, purpose = "DataFeaturesExportAction")
public class MarketDataExportBarAction implements CmdAction {

    protected Exchangeable instrument;
    protected LocalDate beginDate;
    protected LocalDate endDate;
    protected String outputFile;
    /**
     * level 参数
     */
    protected String level = null;
    protected ExchangeableData data;
    protected BarSeriesLoader loader;
    protected String strokeThreshold = "3t";

    static class RegularIndicator{
        EMAIndicator ema;
        SMAIndicator sma;
        RSIIndicator rsi;
        WilliamsRIndicator wri;
        CCIIndicator cci;
        PUBUIndicator pbx;
        StochasticOscillatorKIndicator sok;
        StochasticOscillatorDIndicator sod;
        KDJIndicator kdj;
        StochasticRSIIndicator srsi;
        PercentBIndicator bollp;
        BIASIndicator bias;
        int regular;

        RegularIndicator(BarSeries series, Indicator<Num> indicator, int regular){
            ema = new EMAIndicator(indicator, regular);
            sma = new SMAIndicator(indicator, regular);
            rsi = new RSIIndicator(indicator, regular);
            wri = new WilliamsRIndicator(series, regular);
            cci = new CCIIndicator(series, regular);
            pbx = new PUBUIndicator(indicator, regular);
            sok = new StochasticOscillatorKIndicator(series, regular);
            sod = new StochasticOscillatorDIndicator(sok);
            kdj = KDJIndicator.create(series, regular);
            srsi = new StochasticRSIIndicator(series, regular);
            bollp = new PercentBIndicator(indicator, regular, 2);
            bias = new BIASIndicator(indicator, regular);
            this.regular = regular;
        }

        public List<String> getColumneNames(){
            List<String> result = new ArrayList<>();
            result.add("ema"+regular);
            result.add("sma"+regular);
            result.add("rsi"+regular);
            result.add("wri"+regular);
            result.add("cci"+regular);
            result.add("pbx"+regular);

            result.add("sod"+regular);

            result.add("rsv"+regular);
            result.add("k"+regular);
            result.add("d"+regular);
            result.add("j"+regular);

            result.add("srsi"+regular);
            result.add("bollp"+regular);
            result.add("bias"+regular);
            return result;
        }

        public void csvSet(CSVWriter csv, int idx) {
            csv.set("ema"+regular, ""+ema.getValue(idx).toString());
            csv.set("sma"+regular, ""+sma.getValue(idx).toString());
            csv.set("rsi"+regular, ""+rsi.getValue(idx).toString());
            csv.set("wri"+regular, ""+wri.getValue(idx).toString());
            csv.set("cci"+regular, ""+cci.getValue(idx).toString());
            csv.set("pbx"+regular, ""+pbx.getValue(idx).toString());

            csv.set("sod"+regular, ""+sod.getValue(idx).toString());

            csv.set("rsv"+regular, ""+kdj.getRSVIndicator().getValue(idx).toString());
            csv.set("k"+regular, ""+kdj.getKIndicator().getValue(idx).toString());
            csv.set("d"+regular, ""+kdj.getDIndicator().getValue(idx).toString());
            csv.set("j"+regular, ""+kdj.getValue(idx).toString());

            csv.set("srsi"+regular, ""+srsi.getValue(idx).toString());
            csv.set("bollp"+regular, ""+bollp.getValue(idx).toString());
            csv.set("bias"+regular, ""+bias.getValue(idx).toString());
        }
    }

    static class FeatureContext{
        ExchangeableTradingTimes tradingTimes;
        LeveledBarSeries series;
        Bar2 bar;
        int index;
        List<RegularIndicator> regularIndicators = new ArrayList<>();
        MACDIndicator macd;
        ADXIndicator adx_14_6;
        ADXIndicator adx_14_10;
        ADXIndicator adx_14_14;
    }

    private CSVWriter csv;

    @Override
    public String getCommand() {
        return "marketData.exportBar";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("marketData exportBar --batchFile=<BATCH_FILE> --instrument=<EXCHANGEABLE> --beginDate=<BEGIN_DATE> --endDate=<END_DATE> --strokeThreshold=<TICK_COUNT> --level=min1/min3/min5/min15/vol1k/vol2k/volDaily --outputFile=<OUTPUT_FILE>");
        writer.println("\t导出品种的KBAR数据特征");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        for(List<KVPair> options0:parseBatchOptions(options)) {
            parseOptions(options0);
            data = TraderHomeUtil.getExchangeableData();
            loader = new BarSeriesLoader(beansContainer, data);
            loader.setInstrument(instrument)
                .setStartTradingDay(beginDate)
                .setEndTradingDay(endDate)
                .setLevel(PriceLevel.valueOf(level));
            //1 构造BAR
            buildBars(writer);
        }
        //2 输出CSV
        FileUtil.save(new File(outputFile), csv.toString());
        return 0;
    }

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
        instrument = null;
        beginDate = null;
        endDate = null;
        outputFile = null;
        strokeThreshold = "3t";
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
            case "linewidth":
            case "strokethreshold":
                strokeThreshold =kv.v.toLowerCase();
                break;
            case "level":
                this.level = kv.v.toLowerCase();
                switch(level.toLowerCase()) {
                case "areaconfirm":
                    fileExt = "json";
                    break;
                }
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
            outputFile = instrument+"-"+this.level+"."+fileExt;
        }
    }

    protected long getStrokeThreshold() {
        long unit = 1;
        String s = strokeThreshold;
        if ( s.endsWith("t")) {
            unit = instrument.getPriceTick();
            s = s.substring(0, s.length()-1);
        }else {
            unit = PriceUtil.price2long(1.0);
        }
        return ConversionUtil.toInt(s)*unit;
    }

    protected void buildBars(PrintWriter writer) throws Exception
    {
        writer.print("加载数据 "+instrument+" "+DateUtil.date2str(beginDate)+"-"+DateUtil.date2str(endDate)+" : ");
        LocalDate currDate = beginDate;
        FeatureContext context = new FeatureContext();

        LeveledBarSeries series = loader.setStartTradingDay(beginDate).setEndTradingDay(endDate).load();
        context.series = series;
        context.tradingTimes = instrument.exchange().getTradingTimes(instrument, currDate);
        initFeatures(context);
        createCSVWriter(context);
        buildFeatures(context);
        writer.println(" 输出文件: "+outputFile);
    }

    private void createCSVWriter(FeatureContext context) {
        List<String> columns = new ArrayList<>();
        columns.add("TradingDay");
        columns.add("Index");
        columns.add("BeginTime");
        columns.add("Duration");
        columns.add("EndTime");
        columns.add("BeginTradingTime");
        columns.add("EndTradingTime");
        columns.add("Open");
        columns.add("Close");
        columns.add("High");
        columns.add("Low");
        columns.add("Avg");
        columns.add("MktAvg");
        columns.add("Volume");
        columns.add("OpenInt");

        columns.add("macd");
        columns.add("macd.diff");
        columns.add("macd.dea");
        columns.add("adx-14-6");
        columns.add("adx-14-10");
        columns.add("adx-14-14");

        for(RegularIndicator regularIndicator: context.regularIndicators) {
            columns.addAll(regularIndicator.getColumneNames());
        }

        csv = new CSVWriter<>(columns.toArray(new String[columns.size()]));
    }

    private void buildFeatures(FeatureContext context) {
        LeveledBarSeries series = context.series;
        initFeatures(context);
        LocalDate currTradingDay=null;
        int currIndex=0;
        long lastOpenInt = ((Bar2)series.getBar(0)).getOpenInterest();
        for(int i=0;i<series.getBarCount();i++) {
            Bar2 bar = (Bar2)series.getBar(i);
            if ( !bar.getTradingTimes().getTradingDay().equals(currTradingDay)) {
                currTradingDay = bar.getTradingTimes().getTradingDay();
                currIndex = 0;
                context.tradingTimes = bar.getTradingTimes();
            } else {
                currIndex++;
            }
            csv.next();
            csv.set("TradingDay", DateUtil.date2str(currTradingDay));
            csv.set("Index", ""+currIndex);
            csv.set("BeginTime", DateUtil.date2str(bar.getBeginTime().toLocalDateTime()));
            csv.set("Duration", ""+bar.getTimePeriod().getSeconds());
            csv.set("EndTime", DateUtil.date2str(bar.getEndTime().toLocalDateTime()));
            csv.set("Open", bar.getOpenPrice().toString());
            csv.set("High", bar.getHighPrice().toString());
            csv.set("Low", bar.getLowPrice().toString());
            csv.set("Close", bar.getClosePrice().toString());
            csv.set("Avg", bar.getAvgPrice().toString());
            csv.set("MktAvg", bar.getMktAvgPrice().toString());
            csv.set("Volume", ""+bar.getVolume().intValue());
            csv.set("OpenInt", ""+(bar.getOpenInterest()-lastOpenInt));

            context.bar = bar;
            context.index = i;
            buildTimeFeatures(context, csv);
            buildIndicatorFeatures(context, csv);
            lastOpenInt = bar.getOpenInterest();
        }
    }

    private void initFeatures(FeatureContext context) {
        LeveledBarSeries series = context.series;
        ClosePriceIndicator closePrices = new ClosePriceIndicator(series);
        context.regularIndicators.add(new RegularIndicator(series, closePrices, 4));
        context.regularIndicators.add(new RegularIndicator(series, closePrices, 6));
        context.regularIndicators.add(new RegularIndicator(series, closePrices, 9));
        context.regularIndicators.add(new RegularIndicator(series, closePrices, 12));
        context.regularIndicators.add(new RegularIndicator(series, closePrices, 20));
        context.regularIndicators.add(new RegularIndicator(series, closePrices, 24));
        context.regularIndicators.add(new RegularIndicator(series, closePrices, 33));
        context.regularIndicators.add(new RegularIndicator(series, closePrices, 60));
        context.regularIndicators.add(new RegularIndicator(series, closePrices, 133));
        context.macd = new MACDIndicator(closePrices);
        context.adx_14_6 = new ADXIndicator(series, 14, 6);
        context.adx_14_10 = new ADXIndicator(series, 14, 10);
        context.adx_14_14 = new ADXIndicator(series, 14, 14);
    }

    private void buildTimeFeatures(FeatureContext context, CSVWriter csv) {
        Bar2 bar = context.bar;
        int beginTradingTime = context.tradingTimes.getTradingTime(bar.getBeginTime().toLocalDateTime());
        csv.set("BeginTradingTime", ""+beginTradingTime/1000);
        int endTradingTime = context.tradingTimes.getTradingTime(bar.getEndTime().toLocalDateTime());
        csv.set("EndTradingTime", ""+endTradingTime/1000);
    }

    private void buildIndicatorFeatures(FeatureContext context, CSVWriter csv) {
        int idx = context.index;
        csv.set("macd", ""+context.macd.getValue(idx).toString());
        csv.set("macd.diff", ""+context.macd.getDIFF().getValue(idx).toString());
        csv.set("macd.dea", ""+context.macd.getDEA().getValue(idx).toString());
        csv.set("adx-14-6", ""+context.adx_14_6.getValue(idx).toString());
        csv.set("adx-14-10", ""+context.adx_14_10.getValue(idx).toString());
        csv.set("adx-14-14", ""+context.adx_14_14.getValue(idx).toString());

        for(RegularIndicator regularIndicator: context.regularIndicators) {
            regularIndicator.csvSet(csv, idx);
        }
    }

}
