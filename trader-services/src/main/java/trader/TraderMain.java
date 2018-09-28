package trader;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import trader.common.config.XMLConfigProvider;
import trader.common.util.EncryptionUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.config.ConfigServiceImpl;

@SpringBootApplication
public class TraderMain {

    public static void main(String[] args) throws Throwable {
        if (args.length > 0) {
            Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            logger.setLevel(Level.WARN);
            initServices();
            processArgs(args);
            return;
        }
        initServices();
        System.out.println("Starting trader from home " + TraderHomeUtil.getTraderHome());
        ConfigurableApplicationContext context = SpringApplication.run(TraderMain.class, args);
    }

    private static void initServices() throws Exception {
        File traderHome = TraderHomeUtil.getTraderHome();
        EncryptionUtil.createKeyFile((new File(traderHome, "etc/trader-key.ini")).getAbsolutePath());
        EncryptionUtil.loadKeyFile((new File(traderHome, "etc/trader-key.ini")).getAbsolutePath());
        ConfigServiceImpl.staticRegisterProvider("TRADER", new XMLConfigProvider(new File(traderHome, "etc/trader.xml")));
    }

    private static void processArgs(String[] args) {
        String cmd = args[0];
        switch (cmd.toLowerCase()) {
        case "help":{
            System.out.println("Usage:");
            System.out.println("\thelp: print this message");
            System.out.println("\tencrypt PLAIN_TEXT");
            System.out.println("\tdecrypt ENCRYPTED_DATA");
        }
            break;
        case "encrypt": {
            String result = EncryptionUtil.symmetricEncrypt(args[1].getBytes(StringUtil.UTF8));
            System.out.println(result);
        }
            break;
        case "decrypt": {
            String result = new String(EncryptionUtil.symmetricDecrypt(args[1]), StringUtil.UTF8);
            System.out.println(result);
        }
            break;
        default:
            System.out.println("Unsupported command: "+cmd);
            break;
        }
    }

}
