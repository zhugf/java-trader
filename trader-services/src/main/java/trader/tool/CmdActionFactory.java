package trader.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import trader.common.beans.BeansContainer;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginService;
import trader.service.util.CmdAction;

/**
 * 命令行实际命令的加载和执行
 */
public class CmdActionFactory {
    private List<CmdAction> actions;

    public CmdActionFactory(BeansContainer beansContainer) throws Exception {
        actions = createActions(beansContainer);
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

    private List<CmdAction> createActions(BeansContainer beansContainer) throws Exception {
        List<CmdAction> result = new ArrayList<>();
        result.add(new CryptoEncryptAction());
        result.add(new CryptoDecryptAction());
        result.add(new MarketDataImportAction());
        result.add(new RepositoryArchiveAction());
        result.add(new ServiceAction());
        result.add(new BacktestAction());
        //加载Cmd Action
        try{
            PluginService pluginService = beansContainer.getBean(PluginService.class);
            for(Plugin plugin : pluginService.search(Plugin.PROP_EXPOSED_INTERFACES + "=" + CmdAction.class.getName())) {
                Map<String, CmdAction> cmdActions = plugin.getBeansOfType(CmdAction.class);
                result.addAll(cmdActions.values());
            }
        }catch(Throwable t) {}

        Collections.sort(result, (CmdAction a1, CmdAction a2)->{
            String cmd1 = Arrays.asList(a1.getCommand()).toString();
            String cmd2 = Arrays.asList(a2.getCommand()).toString();

            return cmd1.compareTo(cmd2);
        });
        return result;
    }
}
