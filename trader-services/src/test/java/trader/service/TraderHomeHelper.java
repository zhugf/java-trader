package trader.service;

import java.io.File;

import trader.common.config.XMLConfigProvider;
import trader.common.util.TraderHomeUtil;
import trader.service.config.ConfigServiceImpl;

/**
 * 自动设置 TraderHome相关的系统属性
 */
public class TraderHomeHelper {

    private static File DEFAULT_CFG_FILE = new File( TraderHomeHelper.class.getClassLoader().getResource("etc/trader.xml").getFile());

    private static File lastCfgFile = null;

    public static void init(File cfgFile) {
        if ( cfgFile ==null ) {
            cfgFile = DEFAULT_CFG_FILE;
        }

        if ( lastCfgFile!=null && lastCfgFile.equals(cfgFile) ) {
            return;
        }

        try {
            File traderHome = DEFAULT_CFG_FILE.getParentFile().getParentFile();
            System.setProperty(TraderHomeUtil.PROP_TRADER_HOME, traderHome.getAbsolutePath());
            File dataDir = new File(traderHome, "data");
            System.setProperty(TraderHomeUtil.PROP_REPOSITORY_DIR, dataDir.getAbsolutePath());

            System.setProperty(TraderHomeUtil.PROP_TRADER_CONFIG_FILE, traderHome.getAbsolutePath());
            ConfigServiceImpl.staticRegisterProvider("TRADER", new XMLConfigProvider(cfgFile));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        lastCfgFile = cfgFile;

    }

}
