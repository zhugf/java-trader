package trader.tool;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.Future;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVUtil;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.service.util.CmdAction;

/**
 * 行情数据在某个时间段的主力合约
 */
public class RepositoryPrimaryInstrumentAction implements CmdAction {

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

    private PrintWriter writer;
    private ExchangeableData data;
    private LocalDate beginDate;
    private LocalDate endDate;
    private List<String> contracts;
    private String formatText;

    @Override
    public String getCommand() {
        return "repository.primaryInstrument";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("repository primaryInstrument [--contracts=name1,name2] --beginDate=<BEGIN_DATE> --endDate=<END_DATE> --format=<FormatString>");
        writer.println("\t更新合约每日统计数据");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        data = TraderHomeUtil.getExchangeableData();;
        this.writer = writer;
        parseOptions(options);

        if ( StringUtil.isEmpty(formatText)) {
            writer.println("instrument,primaryBeginDate,beginDate,primaryEndDate,endDate");
        } else if (StringUtil.equalsIgnoreCase(formatText, "dailyPrimaryInstruments")) {
            writer.println("contract,tradingDay,pri_instrument,pri_oi,pri_vol,sec_instrument,sec_oi,sec_vol");
        }

        for(String contract:contracts) {
            Exchangeable exchangeable = Exchangeable.fromString(contract);
            if ( exchangeable.getType()!=ExchangeableType.FUTURE ) {
                writer.println("忽略非期货品种: "+contract);
                continue;
            }
            String dayStatsCsv = data.load(exchangeable, ExchangeableData.DAYSTATS, null);
            TreeMap<LocalDate, DayStats> primaryInstruments = buildDayStats(dayStatsCsv);
            if (StringUtil.equalsIgnoreCase(formatText, "dailyPrimaryInstruments")) {
                printDailyPrimaryInstruments(contract, primaryInstruments);
            } else {
                printDayStats(primaryInstruments);
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
            case "contracts":
                contracts = Arrays.asList(StringUtil.split(kv.v, ",|;"));
                break;
            case "begindate":
                beginDate = DateUtil.str2localdate(kv.v);
                break;
            case "enddate":
                endDate = DateUtil.str2localdate(kv.v);
                break;
            case "format":
                formatText = kv.v;
                break;
            }
        }

        if ( contracts==null && beginDate==null ) {
            writer.println("需要提供过滤参数");
            System.exit(1);
        }
    }

    private TreeMap<LocalDate, DayStats> buildDayStats(String csvText) {
        //首先找到每日主力
        TreeMap<LocalDate, DayStats> primaryInstruments = new TreeMap<>();
        CSVDataSet csv = CSVUtil.parse(csvText);
        while(csv.next()) {
            LocalDate day = csv.getDate("TradingDay");
            if ( beginDate!=null && day.isBefore(beginDate)) {
                continue;
            }
            if ( endDate!=null && day.isAfter(endDate)) {
                continue;
            }
            String instrument = csv.get("InstrumentId");
            if ( Exchangeable.fromString(instrument).getType()!=ExchangeableType.FUTURE) {
                continue;
            }
            long vol = csv.getLong("Volume");
            long oi = csv.getLong("EndOpenInt");
            if ( vol==0 || oi==0 ) {
                continue;
            }
            DayStats dayStats0 = primaryInstruments.get(day);
            if (null==dayStats0) {
                dayStats0 = new DayStats();
                primaryInstruments.put(day, dayStats0);
            }
            dayStats0.merge(instrument, vol, oi);
        }
        return primaryInstruments;
    }

    private void printDailyPrimaryInstruments(String contract, TreeMap<LocalDate, DayStats> primaryInstruments) {
        for(LocalDate day:primaryInstruments.keySet()) {
            DayStats dayStats = primaryInstruments.get(day);
            writer.println(contract
                    +","+DateUtil.date2str(day)
                    +","+dayStats.pri_instrument
                    +","+dayStats.pri_oi
                    +","+dayStats.pri_vol
                    +","+dayStats.sec_instrument
                    +","+dayStats.sec_oi
                    +","+dayStats.sec_vol
                    );
        }
    }

    /**
     * 统计期货品种主力起始,结束日期.
     */
    private void printDayStats(TreeMap<LocalDate, DayStats> primaryInstruments) {
        //首先找到每日主力
        //接下来找到主力合约
        TreeSet<String> piNames = new TreeSet<>();
        for(DayStats dayStats:primaryInstruments.values()) {
            piNames.add(dayStats.pri_instrument);
        }
        //为每个主力合约找到第一天和最后一天
        for(String pi:piNames) {
            LocalDate firstDay = null, lastDay = null;
            for(LocalDate day:primaryInstruments.keySet()) {
                DayStats dayStats = primaryInstruments.get(day);
                if ( dayStats.pri_instrument.equals(pi)) {
                    if ( firstDay==null ) {
                        firstDay = day;
                    }
                    lastDay = day;
                }
            }
            Exchangeable instrument = Exchangeable.fromString(pi);
            LocalDate firstDay0 = firstDay.minusWeeks(2);
            //输出主力合约的时间
            LocalDate lastDay0 = lastDay.plusWeeks(1);
            if ( StringUtil.isEmpty(formatText)) {
                writer.println(instrument+","+DateUtil.date2str(firstDay)+","+DateUtil.date2str(firstDay0)+","+DateUtil.date2str(lastDay)+","+DateUtil.date2str(lastDay0));
            } else {
                String line = String.format(formatText, instrument, DateUtil.date2str(firstDay), DateUtil.date2str(firstDay0), DateUtil.date2str(lastDay), DateUtil.date2str(lastDay0));
                writer.println(line);
            }
        }
    }

}
