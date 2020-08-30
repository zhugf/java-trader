package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
import trader.service.util.CmdAction;

public class RepositoryExportTradingDaysAction extends AbsCmdAction {

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
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        parseOptions(options);
        if ( null==outputFile ) {
            outputFile = "tradingDays.csv";
        }

        ExchangeableData data = TraderHomeUtil.getExchangeableData();;
        Exchangeable instrument = Exchangeable.fromString("au.shfe");

        CSVDataSet csvDataSet = CSVUtil.parse(data.load(instrument, ExchangeableData.DAYSTATS, null));
        TreeSet<LocalDate> tradingDays = new TreeSet<>();
        while(csvDataSet.next()) {
            String tradingDay0 = csvDataSet.get(ExchangeableData.COLUMN_TRADINGDAY);
            tradingDays.add(DateUtil.str2localdate(tradingDay0));
        }
        LinkedList<LocalDate> days = new LinkedList<>(tradingDays);
        CSVWriter csvWriter = new CSVWriter(ExchangeableData.COLUMN_TRADINGDAY, "Continuous");
        LocalDate lastDay = null;
        for(LocalDate day:days) {
            csvWriter.next();
            csvWriter.set(ExchangeableData.COLUMN_TRADINGDAY, DateUtil.date2str(day));
            csvWriter.set("Continuous", ""+isContinuousDay(lastDay, day));
            lastDay = day;
        }
        FileUtil.save(new File(outputFile), csvWriter.toString());
        return 0;
    }

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

}
