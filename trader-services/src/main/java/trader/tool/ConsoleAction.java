package trader.tool;

import java.io.PrintWriter;
import java.util.List;

import trader.common.beans.BeansContainer;
import trader.common.util.StringUtil.KVPair;
import trader.service.util.CmdAction;

public class ConsoleAction implements CmdAction {

    @Override
    public String getCommand() {
        return "console";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("console");
        writer.println("\t交互式命令行");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        // TODO Auto-generated method stub
        return 0;
    }

}
