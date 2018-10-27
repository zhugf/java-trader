package trader.tool;

import java.io.PrintWriter;
import java.util.List;

/**
 * 行情数据的归档命令
 */
public class MarketDataArchiveAction implements CmdAction {

    @Override
    public String getCommand() {
        return "marketData.archive";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("marketData archive");
        writer.println("\t归档市场行情数据");
    }

    @Override
    public int execute(PrintWriter writer, List<String> options) throws Exception {
        return 0;
    }

}
