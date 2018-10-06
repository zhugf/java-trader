package trader.common.util;

import java.io.File;
import java.time.LocalDate;

public class TraderHomeUtil {

    public static final String PROP_TRADER_HOME = "trader.home";
    public static final String ENV_TRADER_HOME = "TRADER_HOME";

    public static final String TRADER_ACCOUNTS = "trader_accounts.ini";

    private static File traderHome = null;
    private static File traderDailyDir = null;

    public static File getTraderHome(){
        return traderHome;
    }

    public static File getTraderDailyDir() {
        return traderDailyDir;
    }

    static {
        initHomeProperty();
        traderDailyDir = (new File(TraderHomeUtil.getTraderHome(), "tradeData/"+DateUtil.date2str(LocalDate.now()))).getAbsoluteFile();
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
