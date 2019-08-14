package trader.service.concurrent;

public interface OrderedExecutor {

    /**
     * 异步执行一个任务, key相等的任务以提交顺序执行. key不等的任务并行执行
     *
     * @param key 并行key
     * @param cmd
     */
    public void execute(String key, Runnable cmd);
}
