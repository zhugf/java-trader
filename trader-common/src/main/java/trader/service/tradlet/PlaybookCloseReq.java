package trader.service.tradlet;

import trader.service.trade.TradeConstants.OrderPriceType;

/**
 * Playbook平仓请求
 */
public class PlaybookCloseReq {

    private String actionId;
    private int timeout;
    private long limitPrice;
    private OrderPriceType priceType = OrderPriceType.BestPrice;

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

    public long getLimitPrice() {
        return limitPrice;
    }
    public void setLimitPrice(long limitPrice) {
        this.limitPrice = limitPrice;
    }
    public OrderPriceType getPriceType() {
        return priceType;
    }
    public void setPriceType(OrderPriceType priceType) {
        this.priceType = priceType;
    }

}
