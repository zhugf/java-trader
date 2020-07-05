package trader.common.util;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 使用多线程创建UUID
 */
public class UUIDUtil {
    private static final int MAX_QUEUE_LENGTH = 1024;
    private static LinkedBlockingQueue<UUID> uuids = new LinkedBlockingQueue<UUID>();
    private static boolean generating = false;

    static {
        asyncGenUUIDs();
    }

    /**
     * 启动独立线程批量创建UUID对象
     */
    private static void asyncGenUUIDs() {
        if ( !generating && uuids.size() < MAX_QUEUE_LENGTH ) {
            synchronized(UUIDUtil.class) {
                if ( !generating ) {
                    generating = true;
                    Thread thread = new Thread("uuid-gen-thread") {
                        @Override
                        public void run() {
                            while(uuids.size()<MAX_QUEUE_LENGTH) {
                                uuids.offer(UUID.randomUUID());
                            }
                            generating = false;
                        }
                    };
                    thread.setDaemon(true);
                    thread.start();
                }
            }
        }
    }

    /**
     * 获取一个UUID
     */
    public static UUID genUUID() {
        UUID uuid = uuids.poll();
        if ( null==uuid ) {
            uuid = UUID.randomUUID();
        }
        if ( uuids.size()<=(MAX_QUEUE_LENGTH/5) ) {
            asyncGenUUIDs();
        }
        return uuid;
    }

    /**
     * 以BASE58编码获取一个UUID
     */
    public static String genUUID58() {
        UUID uuid = genUUID();
        return Base58.compressedUUID(uuid);
    }

}
