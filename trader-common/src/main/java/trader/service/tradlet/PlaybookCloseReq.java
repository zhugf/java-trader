package trader.service.tradlet;

/**
 * Playbook平仓请求
 */
public class PlaybookCloseReq {

    private String actionId;
    private int timeout;

    public String getActionId() {
        return actionId;
    }
    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    /**
     * 超时时间(毫秒), 超过这个时间将会修改价格, 强制使用当前市场价平仓.
     * 0 表示使用Playbook的属性ATTR_CLOSE_TIMEOUT所指定的时间
     */
    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

}
