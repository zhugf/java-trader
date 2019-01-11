package trader.service;

import java.io.File;

import trader.common.config.XMLConfigProvider;
import trader.common.util.TraderHomeUtil;
import trader.service.config.ConfigServiceImpl;

/**
 * 自动设置 TraderHome相关的系统属性
 */
public class TraderHomeHelper {

    public static void init() {
    }

    public static void setTraderConfigFile(File file) throws Exception
    {
        System.setProperty(TraderHomeUtil.PROP_TRADER_CONFIG_FILE, file.getAbsolutePath());
        ConfigServiceImpl.staticRegisterProvider("TRADER", new XMLConfigProvider(file));
    }

    static {
        init0();
    }

    private static void init0() {
        File traderXml = new File( TraderHomeHelper.class.getClassLoader().getResource("etc/trader.xml").getFile());
        File file = traderXml.getParentFile().getParentFile();
        System.setProperty(TraderHomeUtil.PROP_TRADER_HOME, file.getAbsolutePath());
        File dataDir = new File(file, "data");
        System.setProperty(TraderHomeUtil.PROP_REPOSITORY_DIR, dataDir.getAbsolutePath());

        System.setProperty(TraderHomeUtil.PROP_TRADER_CONFIG_FILE, traderXml.getAbsolutePath());
        try {
            ConfigServiceImpl.staticRegisterProvider("TRADER", new XMLConfigProvider(traderXml));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
