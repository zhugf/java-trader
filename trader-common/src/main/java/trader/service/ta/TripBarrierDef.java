package trader.service.ta;

import java.util.LinkedHashMap;
import java.util.Map;

import trader.common.util.ConversionUtil;
import trader.common.util.PriceUtil;

/**
 * 三重界限定义: 最高价, 最低价, 时间长度(ms)
 */
public class TripBarrierDef {

    public long maxPrice;

    public long minPrice;

    public int maxTime;

    /**
     * 三重界限定义
     * @param maxPrice 最高价
     * @param minPrice 最低价
     * @param maxTime 时间长度(ms)
     */
    public TripBarrierDef(long maxPrice, long minPrice, int maxTime) {
        this.maxPrice = maxPrice;
        this.minPrice = minPrice;
        this.maxTime = maxTime;
    }

    public TripBarrierDef() {

    }

    public Map<String, Object> toMap(){
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("maxPrice", PriceUtil.long2str(maxPrice));
        map.put("minPrice", PriceUtil.long2str(minPrice));
        map.put("maxTime", maxTime);
        return map;
    }

    public static TripBarrierDef fromMap(Map map) {
        TripBarrierDef result = new TripBarrierDef();
        result.maxPrice = PriceUtil.str2long(ConversionUtil.toString(map.get("maxPrice")));
        result.minPrice = PriceUtil.str2long(ConversionUtil.toString(map.get("minPrice")));
        result.maxTime = ConversionUtil.toInt(map.get("maxTime"));
        return result;
    }

}
