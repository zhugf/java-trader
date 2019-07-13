package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.IniFile;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.SystemUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.util.CmdAction;

/**
 * 启动交易服务
 */
public class ServiceStartAction implements CmdAction {

    private File statusFile;
    private boolean checkTradingtimes;
    private Class appClass;

    public ServiceStartAction(Class appClass, boolean checkTradingtimes) {
        this.appClass = appClass;
        this.checkTradingtimes = checkTradingtimes;
    }

    @Override
    public String getCommand() {
        return "service.start";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("service start");
        writer.println("\t启动服务");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        //解析参数
        init(options);
        ExchangeableTradingTimes tradingTimes = Exchange.SHFE.detectTradingTimes("au", LocalDateTime.now());
        if ( checkTradingtimes && tradingTimes==null ) {
            writer.println(DateUtil.date2str(LocalDateTime.now())+" is not trading time");
            return 1;
        }
        LocalDate tradingDay = null;
        if ( tradingTimes!=null) {
            tradingDay = tradingTimes.getTradingDay();
        }
        long traderPid = getTraderPid();
        if ( traderPid>0 ) {
            writer.println(DateUtil.date2str(LocalDateTime.now())+" Trader process is running: "+traderPid);
            return 1;
        }
        writer.println(DateUtil.date2str(LocalDateTime.now())+" Starting from config "+System.getProperty(TraderHomeUtil.PROP_TRADER_CONFIG_FILE)+", home: " + TraderHomeUtil.getTraderHome()+", trading day: "+tradingDay);
        saveStatusStart();
        ConfigurableApplicationContext context = SpringApplication.run(appClass, options.toArray(new String[options.size()]));
        saveStatusReady();
        statusFile.deleteOnExit();
        context.addApplicationListener(new ApplicationListener<ContextClosedEvent>() {
            public void onApplicationEvent(ContextClosedEvent event) {
                synchronized(statusFile) {
                    statusFile.notify();
                }
            }
        });
        synchronized(statusFile) {
            statusFile.wait();
        }
        return 0;
    }

    private void init(List<KVPair> options) {
        File workDir = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK);
        workDir.mkdirs();
        statusFile = getStatusFile();
    }

    /**
     * 判断pid文件所记载的trader进程是否存在
     */
    private long getTraderPid() throws Exception {
        long result = 0;
        if ( statusFile.exists() && statusFile.length()>0 ) {
            IniFile iniFile = new IniFile(statusFile);
            IniFile.Section section = iniFile.getSection("start");
            long pid = ConversionUtil.toLong( section.get("pid") );
            if ( SystemUtil.isProcessPresent(pid) ) {
                result = pid;
            }
        }
        return result;
    }

    /**
     * 保存status文件 [starting] section
     */
    private void saveStatusStart() {
        String text = "[start]\n"
            +"pid="+SystemUtil.getPid()+"\n"
            +"startTime="+DateUtil.date2str(LocalDateTime.now())+"\n"
            +"traderHome="+TraderHomeUtil.getTraderHome().getAbsolutePath()+"\n"
            +"traderCfgFile="+System.getProperty(TraderHomeUtil.PROP_TRADER_CONFIG_FILE)+"\n"
            +"httpPort="+ConfigUtil.getInt("/BasisService.httpPort", 10080)+"\n"
            ;
        try{
            FileUtil.save(statusFile, text);
        }catch(Throwable t) {}
    }

    /**
     * 保存status文件 [ready] section
     */
    private void saveStatusReady() {
        String text = "\n[ready]\n"
                +"readyTime="+DateUtil.date2str(LocalDateTime.now())+"\n";
        try{
            FileUtil.save(statusFile, text, true);
        }catch(Throwable t) {}
    }

    public static File getStatusFile() {
        File workDir = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK);
        File result = new File(workDir, "status.ini");
        return result;
    }

}
