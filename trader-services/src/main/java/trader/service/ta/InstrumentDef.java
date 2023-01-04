package trader.service.ta;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.StringUtil;

/**
 * 配置对象
 */
public class InstrumentDef {
    public final String key;

    public final String[] levels;
    public final Map<String, Object> options;

    public InstrumentDef(Exchangeable instrument, Map config) {
        this.key = instrument2key(instrument);
        Map<String, Object> config0 = new HashMap<>(config);
        String levels = (String)config0.remove("levels");
        if (StringUtil.isEmpty(levels)) {
            levels = "min1, min3, min5, min15";
        }
        this.levels = StringUtil.split(levels, ",|;");
        this.options = Collections.unmodifiableMap(config0);
    }

    public static String instrument2key(Exchangeable instrument) {
        String commodity = instrument.contract();
        Exchange exchange = instrument.exchange();
        String key = commodity+"."+exchange.name();
        return key.toLowerCase();
    }

}
