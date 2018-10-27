package trader;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import trader.common.config.XMLConfigProvider;
import trader.common.util.EncryptionUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.config.ConfigServiceImpl;
import trader.tool.CmdAction;
import trader.tool.CmdActionFactory;

@SpringBootApplication
public class TraderMain {

    public static void main(String[] args) throws Throwable {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.WARN);
        initServices();
        processArgs(args);
    }

    private static void initServices() throws Exception {
        File traderHome = TraderHomeUtil.getTraderHome();
        EncryptionUtil.createKeyFile((new File(traderHome, "etc/trader-key.ini")).getAbsolutePath());
        EncryptionUtil.loadKeyFile((new File(traderHome, "etc/trader-key.ini")).getAbsolutePath());
        String traderConfigFile = System.getProperty("trader.configFile", (new File(traderHome, "etc/trader.xml")).getAbsolutePath() );

        ConfigServiceImpl.staticRegisterProvider("TRADER", new XMLConfigProvider(new File(traderConfigFile)));
    }

    private static void processArgs(String[] args) throws Exception
    {
        PrintWriter pw = new PrintWriter(System.out, true);
        CmdActionFactory actionFactory = new CmdActionFactory();
        if (args.length==0 || args[0].toLowerCase().equals("help")) {
            System.out.println("Usage:");
            for(CmdAction action:actionFactory.getActions()) {
                action.usage(pw);
            }
            return;
        }

        //解析
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
        //执行
        if ( currAction==null ) {
            System.out.println("Unknown command arguments: "+Arrays.asList(args));
            System.exit(1);
        }
        List<String> actionProps = new ArrayList<>();
        if ( paramsIndex<args.length) {
            for(int i=paramsIndex;i<args.length;i++) {
                actionProps.add(args[i]);
            }
        }
        int result = currAction.execute(pw, actionProps);
        System.exit(result);
    }

}
