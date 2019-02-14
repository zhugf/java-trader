package trader.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 命令行实际命令的加载和执行
 */
public class CmdActionFactory {
    private List<CmdAction> actions;

    public CmdActionFactory() {
        actions = createActions();
    }

    public CmdAction matchAction(String command) {
        for(CmdAction action:actions) {
            if ( action.getCommand().equalsIgnoreCase(command)){
                return action;
            }
        }
        return null;
    }

    public List<CmdAction> getActions(){
        return Collections.unmodifiableList(actions);
    }

    private List<CmdAction> createActions(){
        List<CmdAction> result = new ArrayList<>();
        result.add(new CryptoEncryptAction());
        result.add(new CryptoDecryptAction());
        result.add(new MarketDataImportAction());
        result.add(new MarketDataArchiveAction());
        result.add(new ServiceAction());
        result.add(new BacktestAction());
        Collections.sort(result, (CmdAction a1, CmdAction a2)->{
            String cmd1 = Arrays.asList(a1.getCommand()).toString();
            String cmd2 = Arrays.asList(a2.getCommand()).toString();

            return cmd1.compareTo(cmd2);
        });
        return result;
    }
}
