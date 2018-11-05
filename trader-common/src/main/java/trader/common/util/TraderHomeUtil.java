package trader.common.util;

import java.io.File;

public class TraderHomeUtil {

    public static final String PROP_TRADER_HOME = "trader.home";
    public static final String ENV_TRADER_HOME = "TRADER_HOME";

    public static final String TRADER_ACCOUNTS = "trader_accounts.ini";

    /**
     * 保存已归档的行情数据的总目录
     */
    public static final String DIR_REPOSITORY = "repository";

    /**
     * 交易账户的按交易日目录, 格式为: [TRADER_HOME]/trader/[tradingDay]
     */
    public static final String DIR_TRADER = "trader";
    /**
     * 废弃文件的目录
     */
    public static final String DIR_TRASH = "trash";
    /**
     * 临时保存行情文件的目录, 会在收市后通过工具导入到repository
     */
    public static final String DIR_MARKETDATA = "tmarketData";
    /**
     * 插件所在目录
     */
    public static final String DIR_PLUGIN = "plugin";
    /**
     * 工作临时目录, 程序关闭后可放心删除
     */
    public static final String DIR_WORK = "work";

    private static File traderHome = null;

    public static File getTraderHome(){
        return traderHome;
    }

    public static File getDirectory(String purpose) {
        switch(purpose) {
        case DIR_REPOSITORY:
            return new File(getTraderHome(), "repository");
        case DIR_TRADER:
            return new File(getTraderHome(), "trader");
        case DIR_TRASH:
            return new File(getTraderHome(), "trash");
        case DIR_MARKETDATA:
            return new File(getTraderHome(), "marketData");
        case DIR_PLUGIN:
            return new File(getTraderHome(), "plugin");
        case DIR_WORK:
            return new File(getTraderHome(), "work");
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
        //从userHome自动创建
        if ( null==traderHome ){
            String osName = System.getProperty("os.name").toLowerCase();
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
                traderHome = new File(home,"traderHome");
            }
        }
        if ( null==propTraderHome ) {
            propTraderHome = traderHome.getAbsolutePath();
            System.setProperty(PROP_TRADER_HOME, propTraderHome);
        }
        File traderLogs = new File(traderHome, "logs");
        traderLogs.mkdirs();
    }

}
