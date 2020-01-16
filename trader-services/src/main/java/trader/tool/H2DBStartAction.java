package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;

import org.h2.tools.Server;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
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
        File h2db = new File( TraderHomeUtil.getTraderHome(), "data/h2db");
        h2db.mkdirs();
        statusFile = new File(h2db, "status.ini");

        int h2TcpPort = ConfigUtil.getInt("/BasisService/h2db.tcpPort", 9092);
        Server.main("-ifNotExists", "-web", "-webAllowOthers", "-tcp", "-tcpAllowOthers", "-tcpPort", ""+h2TcpPort,"-baseDir", h2db.getAbsolutePath());

        String text = "[start]\n"
                +"pid="+SystemUtil.getPid()+"\n"
                +"startTime="+DateUtil.date2str(LocalDateTime.now())+"\n"
                +"traderHome="+TraderHomeUtil.getTraderHome().getAbsolutePath()+"\n"
                +"h2TcpPort="+h2TcpPort+"\n";

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

}
