package trader;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import trader.common.beans.BeansContainer;
import trader.common.config.XMLConfigProvider;
import trader.common.util.EncryptionUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.log.LogServiceImpl;
import trader.service.plugin.PluginService;
import trader.service.plugin.PluginServiceImpl;
import trader.service.util.CmdAction;
import trader.service.util.SimpleBeansContainer;
import trader.tool.CmdActionFactory;
import trader.tool.MainHelper;
import trader.tool.ServiceStartAction;
import trader.ui.config.ConfigServiceImpl;


@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class TraderUIMain {

    public static void main(String[] args) throws Exception
    {
        args = MainHelper.preprocessArgs(args);
        initServices();
        processArgs(args);
    }

    private static void initServices() throws Exception
    {
        System.setProperty(TraderHomeUtil.PROP_DEFAULT_TRADER_CONFIG_NAME, "trader-ui");
        TraderHomeUtil.getTraderHome(); //初始化TraderHome
        String traderConfigFile = System.getProperty(TraderHomeUtil.PROP_TRADER_CONFIG_FILE);
        String traderConfigName = System.getProperty(TraderHomeUtil.PROP_TRADER_CONFIG_NAME);
        File uiKeyFile = new File( (new File(traderConfigFile)).getParentFile(), traderConfigName+"-key.ini");

        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.WARN);
        LogServiceImpl.setLogLevel("org.reflections.Reflections", "ERROR");
        ConfigServiceImpl.staticRegisterProvider("TRADER", new XMLConfigProvider(new File(traderConfigFile)));
        EncryptionUtil.createKeyFile(uiKeyFile);
        EncryptionUtil.loadKeyFile(uiKeyFile);
    }

    private static void processArgs(String[] args) throws Exception
    {
        PrintWriter writer = new PrintWriter(System.out, true);

        BeansContainer beansContainer = createBeansContainer();

        CmdActionFactory actionFactory = new CmdActionFactory(beansContainer, new CmdAction[] {
                new ServiceStartAction(TraderUIMain.class, false)
        });
        if (args.length==0 || args[0].toLowerCase().equals("help")) {
            writer.println("Usage:");
            for(CmdAction action:actionFactory.getActions()) {
                action.usage(writer);
            }
            return;
        }

        //匹配CmdAction
        CmdAction currAction = null;
        String currCmd = "";
        int paramsIndex = -1;
        for(int i=0;i<args.length;i++) {
            String arg = args[i];
            if ( i==0 ) {
                currCmd = arg;
            }else {
                currCmd += ("."+arg);
            }
            currAction = actionFactory.matchAction(currCmd);
            if ( currAction!=null ) {
                paramsIndex = i+1;
                break;
            }
        }
        if ( currAction==null ) {
            System.out.println("未知命令行参数: "+Arrays.asList(args));
            System.exit(1);
        }
        //解析Action参数
        List<String> actionProps = new ArrayList<>();
        if ( paramsIndex<args.length) {
            for(int i=paramsIndex;i<args.length;i++) {
                actionProps.add(args[i]);
            }
        }
        int result = currAction.execute(beansContainer, writer, StringUtil.args2kvpairs(actionProps));
        System.exit(result);
    }

    private static BeansContainer createBeansContainer() throws Exception
    {
        SimpleBeansContainer result = new SimpleBeansContainer();
        PluginServiceImpl pluginService = new PluginServiceImpl();
        pluginService.init();
        result.addBean(PluginService.class, pluginService);
        return result;
    }

}
