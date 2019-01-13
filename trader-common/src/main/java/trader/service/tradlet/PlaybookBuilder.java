package trader.service.tradlet;

import trader.service.trade.TradeConstants.PosDirection;

/**
 * 交易剧本创建
 */
public class PlaybookBuilder {
    private int volume;
    private PosDirection openDirection;
    private long openPrice;
    private int openTimeout;

    public int volume() {
        return volume;
    }

    public PlaybookBuilder volume(int volume) {
        this.volume = volume;
        return this;
    }
}
