package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;

import trader.common.beans.BeansContainer;
import trader.common.util.ConversionUtil;
import trader.common.util.IniFile;
import trader.common.util.StringUtil.KVPair;
import trader.service.util.CmdAction;

public class ServiceStatusAction implements CmdAction {

    @Override
    public String getCommand() {
        return "service.status";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("service status");
        writer.println("\t交易服务运行状态");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        File statusFile = ServiceStartAction.getStatusFile();
        IniFile statusIni = null;
        if ( statusFile.exists() && statusFile.length()>0 ) {
            statusIni = new IniFile(statusFile);
            IniFile.Section startSection = statusIni.getSection("start");
            IniFile.Section readySection = statusIni.getSection("ready");
            String status = null;
            long pid = ConversionUtil.toLong( startSection.get("pid") );
            Optional<ProcessHandle> process = ProcessHandle.of(pid);
            if ( !process.isPresent() ) {
                status = "Closed";
            }else {
                if ( readySection==null ) {
                    status = "Starting: "+pid;
                }else {
                    status = "Started: "+pid;
                }
            }

            writer.println("Status: "+status);
            writer.println("Start time: "+startSection.get("startTime"));
        }
        return 0;
    }

}
