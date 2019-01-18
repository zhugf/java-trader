package trader.service.tradlet;

import java.util.Properties;

import trader.service.trade.TradeConstants.PosDirection;

/**
 * 交易剧本创建
 */
public class PlaybookBuilder {
    private int volume;
    private PosDirection openDirection;
    private long openPrice;
    private String openTimeout;
    private Properties properties = new Properties();
    private String templateId;

    public int getVolume() {
        return volume;
    }

    public PlaybookBuilder setVolume(int volume) {
        this.volume = volume;
        return this;
    }

    public PosDirection getOpenDirection() {
        return openDirection;
    }

    public PlaybookBuilder setOpenDirection(PosDirection dir) {
        this.openDirection = dir;
        return this;
    }

    public long getOpenPrice() {
        return openPrice;
    }

    public String getOpenTimeout() {
        return openTimeout;
    }

    public PlaybookBuilder setOpenPrice(long openPrice) {
        this.openPrice = openPrice;
        return this;
    }

    public PlaybookBuilder setOpenTimeout(String openTimeout) {
        this.openTimeout = openTimeout;
        return this;
    }

    public Properties getProperties() {
        return properties;
    }

    public PlaybookBuilder setProperty(String key, String value) {
        properties.setProperty(key, value);
        return this;
    }

    public String getTemplateId() {
        return templateId;
    }

    public PlaybookBuilder setTemplateId(String templateId) {
        this.templateId = templateId;
        return this;
    }

}
