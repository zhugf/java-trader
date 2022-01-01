package trader.common.beans;

public enum ServiceState {
    /**
     * 待初始化
     */
    NotInited(false),
    /**
     * 启动中, 不可工作
     */
    Starting(false),
    /**
     * 启动完毕, 正常工作
     */
    Ready(true),
    /**
     * 已关闭
     */
    Stopped(true);

    private final boolean done;

    private ServiceState(boolean done) {
        this.done = done;
    }

    public boolean isDone() {
        return done;
    }
}
