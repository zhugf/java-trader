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
            writer.println("instrument,beginDate,beginDate0,endDate,endDate0");
        }
        for(String contract:contracts) {
            Exchangeable exchangeable = Exchangeable.fromString(contract);
            if ( exchangeable.getType()!=ExchangeableType.FUTURE ) {
                writer.println("忽略非期货品种: "+contract);
                continue;
            }
            Future future = (Future)exchangeable;
            String dayStatsCsv = data.load(exchangeable, ExchangeableData.DAYSTATS, null);
            printDayStats(dayStatsCsv);
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

    private static class DayStats{
        long openInt;
        String instrument;
    }

    private void printDayStats(String csvText) {
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
            DayStats dayStats = new DayStats();
            dayStats.instrument = csv.get("InstrumentId");
            if ( Exchangeable.fromString(dayStats.instrument).getType()!=ExchangeableType.FUTURE) {
                continue;
            }
            dayStats.openInt = csv.getLong("EndOpenInt");
            DayStats dayStats0 = primaryInstruments.get(day);
            if ( dayStats0!=null && dayStats0.openInt<dayStats.openInt) {
                dayStats0 = null;
            }
            if ( dayStats0==null ) {
                primaryInstruments.put(day, dayStats);
            }
        }

        //接下来找到主力合约
        TreeSet<String> piNames = new TreeSet<>();
        for(DayStats dayStats:primaryInstruments.values()) {
            piNames.add(dayStats.instrument);
        }

        //为每个主力合约找到第一天和最后一天
        for(String pi:piNames) {
            LocalDate firstDay = null, lastDay = null;
            for(LocalDate day:primaryInstruments.keySet()) {
                DayStats dayStats = primaryInstruments.get(day);
                if ( dayStats.instrument.equals(pi)) {
                    if ( firstDay==null ) {
                        firstDay = day;
                    }
                    lastDay = day;
                }
            }
            Exchangeable instrument = Exchangeable.fromString(pi);
            LocalDate firstDay0 = firstDay.minusMonths(1);
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
