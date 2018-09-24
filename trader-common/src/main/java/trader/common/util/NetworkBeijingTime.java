package trader.common.util;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NetworkBeijingTime {

    private static volatile long lastUpdateTime;
    private static long beijingTimeAdjust;
    private static Logger logger = LoggerFactory.getLogger(NetworkBeijingTime.class);
    private static final int idleSeconds = 10*60;

    public static void startUpdateThread(ScheduledExecutorService executorService){
         if ( executorService==null ){
             Thread thread = new Thread("NetworkBeijingTime Update Thread"){
                 @Override
                public void run(){
                     while(true){
                         long beforeRead = System.currentTimeMillis();
                         long beijingTime = getBeiJingTimeFromNetWork();
                         long afterRead = System.currentTimeMillis();
                         long currTime = (afterRead-beforeRead)/2+beforeRead;
                         if ( beijingTime!=0 ){
                             beijingTimeAdjust = beijingTime - currTime;
                             lastUpdateTime = System.currentTimeMillis();
                         }
                         try {
                             Thread.sleep(idleSeconds*1000);
                         } catch (InterruptedException e) {}
                     }
                 }
             };
             thread.setDaemon(true);
             thread.start();
         }else{
             executorService.scheduleWithFixedDelay(()->{
                 long beforeRead = System.currentTimeMillis();
                 long beijingTime = getBeiJingTimeFromNetWork();
                 long afterRead = System.currentTimeMillis();
                 long currTime = (afterRead-beforeRead)/2+beforeRead;
                 if ( beijingTime!=0 ){
                     beijingTimeAdjust = beijingTime - currTime;
                     lastUpdateTime = System.currentTimeMillis();
                 }
             }, 0, idleSeconds, TimeUnit.SECONDS);
         }
    }

    public static long getTime(){
        return (System.currentTimeMillis()+beijingTimeAdjust);
    }

    public static long getLastUpdateTime(){
        return lastUpdateTime;
    }

    private static long getBeiJingTimeFromNetWork() {
        long time = 0;
        String urls = "http://open.baidu.com/special/time/";
        try {
            String content = NetUtil.readHttpAsText(urls, Charset.forName("GBK"));
            BufferedReader br = new BufferedReader(new StringReader(content));
            String line;
            int index = 0;
            while ((line = br.readLine()) != null) {
                index++;
                if (index < 123)
                    continue;
                if (line.indexOf("window.baidu_time(") != -1) {
                    String[] s = line.split("\\(");
                    time = Long.parseLong(s[1].substring(0, s[1].length() - 2));
                    return time;
                }
            }
        } catch (Throwable t) {
            logger.error("Load network time from baidu failed", t);
        }
        return time;
    }

}
