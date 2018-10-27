package trader.tool;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import trader.TraderMain;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.TraderHomeUtil;

public class ServiceAction implements CmdAction {

    @Override
    public String getCommand() {
        return "service";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("service");
        writer.println("\t启动交易服务");
    }

    @Override
    public int execute(PrintWriter writer, List<String> options) throws Exception {
        if ( !MarketDayUtil.isMarketDay(Exchange.SHFE, LocalDate.now()) ) {
            writer.println("Trader auto close in non-trading day: "+LocalDate.now());
            return 1;
        }
        writer.println("Starting trader from home " + TraderHomeUtil.getTraderHome());
        ConfigurableApplicationContext context = SpringApplication.run(TraderMain.class, options.toArray(new String[options.size()]));
        return 0;
    }

}
