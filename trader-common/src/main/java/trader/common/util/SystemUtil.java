package trader.common.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.common.util.PlatformUtil;

public class SystemUtil {
    private static final int DEFAULT_PROCESS_TIMEOUT = 30;

    public static List<String> execute(String command) throws Exception
    {
        return execute(command, new AtomicInteger());
    }

    public static List<String> execute(String command, AtomicInteger exitValue) throws Exception
    {
        Process process = Runtime.getRuntime().exec(command);
        return execute0(process, exitValue);
    }

    public static List<String> execute(String[] commands) throws Exception
    {
        return execute(commands, new AtomicInteger());
    }

    public static List<String> execute(String[] commands, AtomicInteger exitValue) throws Exception
    {
        Process process = Runtime.getRuntime().exec(commands);
        return execute0(process, exitValue);
    }

    private static List<String> execute0(Process process, AtomicInteger exitValue) throws Exception
    {
        List<String> result = new ArrayList<>();
        try(BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream())); )
        {
            String line;
            while ((line = in.readLine()) != null) {
                result.add(line);
            }
            while ((line = err.readLine()) != null) {
                result.add(line);
            }
        }finally{
            exitValue.set( destroyProcess(process) );
        }
        return result;
    }

    public static long getPid(){
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long result = -1;

        try{
            Method method = runtimeBean.getClass().getMethod("getPid");
            Object r0 = method.invoke(runtimeBean);
            result = ConversionUtil.toLong(r0);
        }catch(Throwable t) {}
        if ( result== -1 ) {
            String pidAtHost = runtimeBean.getName();
            int atIdx = pidAtHost.indexOf('@');
            if (atIdx>0) {
                result = ConversionUtil.toLong(pidAtHost.subSequence(0, atIdx));
            }
        }
        return result;
    }

    public static boolean isProcessPresent(long pid) {
        boolean result = false;
        if ( PlatformUtil.isLinux() ) {
            result = (new File("/proc/"+pid+"/cmdline")).exists();
        }
        return result;
    }

    public static int destroyProcess(Process process){
        return destroyProcess(process, DEFAULT_PROCESS_TIMEOUT);
    }

    public static int destroyProcess(Process process, int waitSeconds){
        if ( process==null ){
            return 0;
        }
        try {
            process.waitFor(waitSeconds, TimeUnit.SECONDS);
        } catch (Throwable e) {}
        try {
            process.getInputStream().close();
        } catch (Throwable e) {}
        try {
            process.getOutputStream().close();
        } catch (Throwable e) {}
        try {
            process.getErrorStream().close();
        } catch (Throwable e) {}
        process.destroy();
        return process.exitValue();
    }

    public static String getHostName() {
        String hostName = System.getenv("HOSTNAME");
        if ( StringUtil.isEmpty(hostName)) {
        File hostnameFile = new File("/etc/hostname");
        if (hostnameFile.exists() ) {
            try {
                hostName = FileUtil.read(hostnameFile).trim();
            }catch(Exception e) {}
            }
        }
        if ( StringUtil.isEmpty(hostName)) {
            try {
                hostName = SystemUtil.execute("hostname").get(0).trim();
            } catch (Throwable e1) {}
        }
        if ( StringUtil.isEmpty(hostName) ) {
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {}
        }
        if ( StringUtil.isEmpty(hostName) ) {
            hostName = "localhost";
        }
        return hostName;
    }

    public static boolean isJava9OrHigher() {
        boolean result = false;
        String vmVersion = ManagementFactory.getRuntimeMXBean().getVmVersion();
        if ( vmVersion.startsWith("1.")) {
            result = false;
        }else {
            result = true;
        }
        return result;
    }

}
