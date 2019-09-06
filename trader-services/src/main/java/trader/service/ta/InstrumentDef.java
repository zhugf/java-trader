package trader.service.ta;

import java.util.Map;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;

/**
 * 配置对象
 */
public class InstrumentDef {
    public final String key;

    public final String commodity;
    public final Exchange exchange;
    public final String[] levels;
    public final long strokeThreshold;
    public final long lineWidth;

    public InstrumentDef(Exchangeable instrument, Map config) {
        this.commodity = instrument.commodity();
        this.exchange = instrument.exchange();
        this.key = commodity+"."+exchange.name();

        String strokeThreshold = (String)config.get("strokeThreshold");
        String lineWidth = (String)config.get("lineWidth");
        String levels = (String)config.get("levels");
        if (StringUtil.isEmpty(levels)) {
            levels = "min1, min5, min15, min30, day";
        }
        this.strokeThreshold = PriceUtil.config2long(strokeThreshold, instrument.getPriceTick());
        this.lineWidth = PriceUtil.config2long(lineWidth, instrument.getPriceTick());
        this.levels = StringUtil.split(levels, ",|;");
    }

}
