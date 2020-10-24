package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

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
import trader.common.util.TraderHomeUtil;
import trader.common.util.StringUtil.KVPair;
import trader.service.util.AbsCmdAction;
import trader.service.util.CmdAction;

public class RepositoryExportTradingDaysAction extends AbsCmdAction {

    public RepositoryExportTradingDaysAction() {
        instrument = Exchangeable.fromString("au.shfe");
    }

    @Override
    public String getCommand() {
        return "repository.exportTradingDays";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("repository exportTradingDays -beginDate=xxx --endDate=yyyy");
        writer.println("\t导出交易日连续性数据");
    }

    @Override
    public int executeImpl(List<KVPair> options) throws Exception
    {
        parseOptions(options);
        if ( null==outputFile ) {
            outputFile = "tradingDays.csv";
        }

        CSVDataSet csvDataSet = CSVUtil.parse(data.load(instrument, ExchangeableData.DAYSTATS, null));
        TreeSet<LocalDate> tradingDays = new TreeSet<>();
        while(csvDataSet.next()) {
            String tradingDay0 = csvDataSet.get(ExchangeableData.COLUMN_TRADINGDAY);
            tradingDays.add(DateUtil.str2localdate(tradingDay0));
        }
        LinkedList<LocalDate> days = new LinkedList<>(tradingDays);
        CSVWriter csvWriter = new CSVWriter(ExchangeableData.COLUMN_TRADINGDAY, "Continuous", "Gap");
        LocalDate lastDay = null;
        for(LocalDate day:days) {
            csvWriter.next();
            csvWriter.set(ExchangeableData.COLUMN_TRADINGDAY, DateUtil.date2str(day));
            csvWriter.set("Continuous", ""+isContinuousDay(lastDay, day));
            csvWriter.set("Gap", ""+getGap(lastDay, day));
            lastDay = day;
        }
        FileUtil.save(new File(outputFile), csvWriter.toString());
        return 0;
    }

    private int getGap(LocalDate lastDay, LocalDate day) {
        int result = 0;
        if ( !isContinuousDay(lastDay, day) && lastDay!=null && day!=null) {
            LocalDate day0 = lastDay;
            while(day0.compareTo(day)<0) {
                day0 = nextWorkingDay(day0);
                result += 1;
            }
            if ( result>0) {
                result--;
            }
        }
        return result;
    }

    /**
     * 是否连续
     */
    private boolean isContinuousDay(LocalDate lastDay, LocalDate day) {
        boolean result = false;
        if ( lastDay!=null && day!=null ) {
            if ( lastDay.plusDays(1).equals(day) ) {
                result = true;
            }
            if ( lastDay.getDayOfWeek()==DayOfWeek.FRIDAY
                    && day.getDayOfWeek()==DayOfWeek.MONDAY
                    && lastDay.plusDays(3).equals(day))
            {
                result = true;
            }
        }
        return result;
    }

    private LocalDate nextWorkingDay(LocalDate day) {
        switch(day.getDayOfWeek()) {
        case FRIDAY:
            return day.plusDays(3);
        case SATURDAY:
            return day.plusDays(2);
        case MONDAY:
        case TUESDAY:
        case WEDNESDAY:
        case THURSDAY:
        case SUNDAY:
            return day.plusDays(1);
        }
        return null;
    }

}
