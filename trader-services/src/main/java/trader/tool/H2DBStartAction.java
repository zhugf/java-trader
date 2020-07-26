package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import org.h2.tools.Server;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.IniFile;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.SystemUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.util.CmdAction;

public class H2DBStartAction implements CmdAction {
    private File statusFile;

    @Override
    public String getCommand() {
        return "h2db.start";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("h2db start");
        writer.println("\t启动H2数据库远程服务");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        File statusFile = getStatusFile();
        File h2db = statusFile.getParentFile();
        String addr = ConfigUtil.getString("/BasisService/h2db.addr");
        int tcpPort = ConfigUtil.getInt("/BasisService/h2db.tcpPort", 9092);
        int webPort = ConfigUtil.getInt("/BasisService/h2db.webPort", 8082);
        if (!StringUtil.isEmpty(addr)) {
            System.setProperty("h2.bindAddress", addr);
        }
        Server.main("-ifNotExists", "-web", "-webAllowOthers", "-webDaemon", "-webPort",""+webPort, "-tcp", "-tcpAllowOthers", "-tcpDaemon", "-tcpPort", ""+tcpPort,"-baseDir", h2db.getAbsolutePath());

        String text = "[start]\n"
                +"pid="+SystemUtil.getPid()+"\n"
                +"startTime="+DateUtil.date2str(LocalDateTime.now())+"\n"
                +"traderHome="+TraderHomeUtil.getTraderHome().getAbsolutePath()+"\n"
                +(addr!=null?"addr="+addr+"\n":"")
                +"webPort="+webPort+"\n"
                +"tcpPort="+tcpPort+"\n";

        FileUtil.save(statusFile, text);
        statusFile.deleteOnExit();
        //tcpServer.start();
        //writer.println(tcpServer.getStatus());
        //tcpServer.setShutdownHandler(this);
        synchronized(statusFile) {
            statusFile.wait();
        }
        return 0;
    }

    private static File getStatusFile() {
        File h2db = new File( TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK), "h2db");
        h2db.mkdirs();
        File statusFile = new File(h2db, "status.ini");
        return statusFile;
    }

    /**
     * 返回当前可用的H2DB服务属性: webPort, tcpPort
     */
    private static Properties getServerStatus(File statusFile) {
        Properties props = null;
        if ( statusFile.exists() ) {
            try {
                IniFile ini = new IniFile(statusFile);
                IniFile.Section section = ini.getSection("start");
                props = section.getProperties();
            }catch(Throwable t) {}
        }
        if ( props!=null ) {
            int pid = ConversionUtil.toInt(props.getProperty("pid"));
            if ( !SystemUtil.isProcessPresent(pid) ) {
                props = null;
            }
        }
        return props;
    }

    public static String getH2DBURL() {
        String url = null;
        File statusFile = getStatusFile();
        File h2db = statusFile.getParentFile();
        Properties serverProps = getServerStatus(statusFile);
        if ( serverProps!=null ) {
            String addr = serverProps.getProperty("addr", "127.0.0.1");
            String tcpPort = serverProps.getProperty("tcpPort");
            url = "jdbc:h2:tcp://"+addr+":"+tcpPort+"/repository;IFEXISTS=FALSE;AUTO_RECONNECT=TRUE";
        } else {
            int tcpPort = ConfigUtil.getInt("/BasisService/h2db.tcpPort", 9092);
            url = "jdbc:h2:"+h2db.getAbsolutePath()+"/repository;IFEXISTS=FALSE;AUTO_RECONNECT=TRUE;AUTO_SERVER=TRUE;AUTO_SERVER_PORT="+tcpPort;
        }
        return url;
    }

}
