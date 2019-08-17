package trader.common.util;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.google.gson.JsonObject;

import net.common.util.PlatformUtil;
import trader.common.config.ConfigUtil;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;

public class TraderHomeUtil {

    public static final String PROP_REPOSITORY_DIR = "trader.repository";
    public static final String PROP_TRADER_HOME = "trader.home";
    public static final String PROP_TRADER_ETC_DIR = "trader.etc";
    public static final String PROP_TRADER_CONFIG_FILE = "trader.configFile";
    /**
     * 该系统属性被logback-spring.xml使用
     */
    public static final String PROP_TRADER_CONFIG_NAME = "trader.configName";
    /**
     * 交易日
     */
    public static final String PROP_TRADER_TRADINGDAY = "trader.tradingDay";

    public static final String PROP_DEFAULT_TRADER_CONFIG_NAME = "trader.defaultConfigName";

    public static final String ENV_TRADER_HOME = "TRADER_HOME";

    public static final String TRADER_ACCOUNTS = "trader_accounts.ini";

    /**
     * 保存已归档的行情数据的总目录
     */
    public static final String DIR_REPOSITORY = "data/repository";
    /**
     * 废弃文件的目录
     */
    public static final String DIR_TRASH = "data/trash";
    /**
     * 临时保存行情文件的目录, 会在收市后通过工具导入到repository
     */
    public static final String DIR_MARKETDATA = "data/marketData";
    /**
     * 插件所在目录
     */
    public static final String DIR_PLUGIN = "plugin";
    /**
     * 工作目录, 程序关闭后可放心删除
     */
    public static final String DIR_WORK = "data/work";

    public static final String DIR_ETC = "etc";

    private static File traderHome = null;

    private static ExchangeableData data;

    public static JsonObject toJson() {
        JsonObject json = new JsonObject();
        RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
        String hostName = SystemUtil.getHostName();
        String exportAddr = ConfigUtil.getString("/BasisService.exportAddr");
        if ( StringUtil.isEmpty(exportAddr)) {
            exportAddr = hostName;
        }
        json.addProperty("hostName", hostName);
        json.addProperty("exportAddr", exportAddr);
        json.addProperty("httpPort", ConfigUtil.getInt("/BasisService.httpPort", 10080));
        json.addProperty("traderHome", System.getProperty(PROP_TRADER_HOME));
        json.addProperty("configName", System.getProperty(PROP_TRADER_CONFIG_NAME));
        json.addProperty("configFile", System.getProperty(PROP_TRADER_CONFIG_FILE));
        json.addProperty("startTime", rtBean.getStartTime());
        return json;
    }

    public static File getTraderHome(){
        return traderHome;
    }

    public static ExchangeableData getExchangeableData() {
        if (data == null) {
            File dataDir = getDirectory(DIR_REPOSITORY);
            String repositoryDir = System.getProperty(PROP_REPOSITORY_DIR);
            if (!StringUtil.isEmpty(repositoryDir)) {
                dataDir = new File(repositoryDir);
            }
            data = new ExchangeableData(dataDir, false);
        }
        return data;
    }

    public static File getDirectory(String purpose) {
        switch(purpose) {
        case DIR_ETC:
            String etcDir = System.getProperty(PROP_TRADER_ETC_DIR);
            if (!StringUtil.isEmpty(etcDir)) {
                return new File(etcDir);
            }
            return new File(getTraderHome(), "etc");
        case DIR_PLUGIN:
            return new File(getTraderHome(), "plugin");
        case DIR_REPOSITORY:
            String repositoryDir = System.getProperty(PROP_REPOSITORY_DIR);
            if ( !StringUtil.isEmpty(repositoryDir)) {
                return new File(repositoryDir);
            }
            return new File(getTraderHome(), "data/repository");
        case DIR_TRASH:
            return new File(getTraderHome(), "data/trash");
        case DIR_MARKETDATA:
            return new File(getTraderHome(), "data/marketData");
        case DIR_WORK:
            String traderConfigName = System.getProperty(PROP_TRADER_CONFIG_NAME);
            File result = new File(getTraderHome(), "data/work/"+traderConfigName);
            result.mkdirs();
            return result;
        }
        return null;
    }

    static {
        initHomeProperty();
    }

    private static void initHomeProperty() {
        String propTraderHome = System.getProperty(PROP_TRADER_HOME);
        //从System Property读取
        if ( null!=propTraderHome ) {
            traderHome = new File(propTraderHome);
        }
        //从ENV读取
        if ( null==traderHome ) {
            String envTraderHome = System.getenv(ENV_TRADER_HOME);
            if ( null!=envTraderHome) {
                traderHome = new File(envTraderHome);
            }
        }
        //检查$HOME/PersonalData/traderHome
        String osName = System.getProperty("os.name").toLowerCase();
        if ( null==traderHome ) {

        }
        //从userHome自动创建
        if ( null==traderHome ){
            if( osName.indexOf("windows")>=0 ){
            	String[] paths = {"C:\\traderHome", "D:\\traderHome", "Z:\\traderHome"};
            	for(String path:paths){
            		File f=new File(path);
            		if ( f.exists() && f.isDirectory() ){
            		    traderHome = f.getAbsoluteFile();
            			break;
            		}
            	}
            	if ( null==traderHome ) {
            	    traderHome = new File("C:\\traderHome");
            	}
            }else{
                String home = System.getProperty("user.home");
                if ( traderHome==null ) {
                    traderHome = new File(home,"traderHome");
                }
            }
        }
        if ( null==propTraderHome ) {
            propTraderHome = traderHome.getAbsolutePath();
            System.setProperty(PROP_TRADER_HOME, propTraderHome);
        }
        if ( StringUtil.isEmpty(System.getProperty(PROP_TRADER_ETC_DIR)) ) {
            System.setProperty(PROP_TRADER_ETC_DIR, getDirectory(DIR_ETC).getAbsolutePath());
        }
        if ( StringUtil.isEmpty(System.getProperty(PROP_REPOSITORY_DIR)) ) {
            System.setProperty(PROP_REPOSITORY_DIR, getDirectory(DIR_REPOSITORY).getAbsolutePath());
        }
        System.setProperty("jtrader.platform", PlatformUtil.getPlatform().name());
        File traderLogs = new File(traderHome, "logs");
        traderLogs.mkdirs();

        String traderConfigFile = System.getProperty(PROP_TRADER_CONFIG_FILE);
        String traderConfigName = null;
        if ( StringUtil.isEmpty(traderConfigFile)) {
            String defaultTraderConfigName =  System.getProperty(PROP_DEFAULT_TRADER_CONFIG_NAME, "trader");
            traderConfigFile = new File(getDirectory(DIR_ETC), defaultTraderConfigName+".xml").getAbsolutePath();
            System.setProperty(PROP_TRADER_CONFIG_FILE, traderConfigFile);
        }
        traderConfigName = FileUtil.getFileMainName(new File(traderConfigFile));
        System.setProperty(TraderHomeUtil.PROP_TRADER_CONFIG_NAME, traderConfigName);

        //trader.tradingDay
        if ( StringUtil.isEmpty(System.getProperty(PROP_TRADER_TRADINGDAY)) ) {
            ExchangeableTradingTimes tradingTimes = Exchange.SHFE.detectTradingTimes("au", LocalDateTime.now());
            if ( tradingTimes!=null) {
                LocalDate tradingDay = tradingTimes.getTradingDay();
                System.setProperty(PROP_TRADER_TRADINGDAY, DateUtil.date2str(tradingDay));
            }
        }
    }

}
