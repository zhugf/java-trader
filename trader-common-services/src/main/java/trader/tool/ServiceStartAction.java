package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private Class appClass;
    private boolean tradingTimesOnly;

    public ServiceStartAction(Class appClass, boolean tradingTimesOnly) {
        this.appClass = appClass;
        this.tradingTimesOnly = tradingTimesOnly;
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
        if ( tradingTimes==null ) {
            if ( tradingTimesOnly ) {
                writer.println(DateUtil.date2str(LocalDateTime.now())+" 非交易时间");
                return 1;
            }
        }
        LocalDate tradingDay = null;
        if ( tradingTimes!=null) {
            tradingDay = tradingTimes.getTradingDay();
        }
        long traderPid = getTraderPid();
        if ( traderPid>0 ) {
            writer.println(DateUtil.date2str(LocalDateTime.now())+" 进程已运行: "+traderPid);
            return 1;
        }
        writer.println(DateUtil.date2str(LocalDateTime.now())+" 启动, 配置文件 "+System.getProperty(TraderHomeUtil.PROP_TRADER_CONFIG_FILE)+", 目录: " + TraderHomeUtil.getTraderHome()+(tradingDay!=null?", 交易日: "+tradingDay:""));
        saveStatusStart();
        List<String> args = new ArrayList<>();
        for(KVPair kv:options) {
            args.add(kv.toString());
        }
        ConfigurableApplicationContext context = SpringApplication.run(appClass, args.toArray(new String[args.size()]));
        saveStatusReady();
        statusFile.deleteOnExit();
        context.addApplicationListener(new ApplicationListener<ContextClosedEvent>() {
            @Override
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
            +"httpPort="+ConfigUtil.getInt("/BasisService/web.httpPort", 10080)+"\n"
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
