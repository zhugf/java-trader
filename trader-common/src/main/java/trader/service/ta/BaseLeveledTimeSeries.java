package trader.service.ta;


import java.util.function.Function;

import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;

public class BaseLeveledTimeSeries extends BaseTimeSeries implements LeveledTimeSeries, JsonEnabled {

    private static final long serialVersionUID = 2904300939512922674L;

    private Exchangeable e;

    private PriceLevel level;

    public BaseLeveledTimeSeries(Exchangeable e, String name, PriceLevel level, Function<Number, Num> numFunction) {
        super(name, numFunction);
        this.level = level;
        this.e = e;
    }

    @Override
    public Exchangeable getExchangeable() {
        return e;
    }

    @Override
    public PriceLevel getLevel() {
        return level;
    }

    @Override
    public TimeSeries getSubSeries(int startIndex, int endIndex){
        if(startIndex > endIndex){
            throw new IllegalArgumentException
                    (String.format("the endIndex: %s must be bigger than startIndex: %s", endIndex, startIndex));
        }
        BaseLeveledTimeSeries result = new BaseLeveledTimeSeries(e, getName(), level, numFunction);
        if(getBarCount()>0) {
            int start = Math.max(startIndex, this.getBeginIndex());
            int end = Math.min(endIndex, this.getEndIndex() + 1);
            if ( end>=start+1 ) {
                for(int i=start; i<end;i ++) {
                    result.addBar(getBar(i));
                }
            }
        }
        return result;
    }

    @Override
    public JsonElement toJson() {
        JsonArray json = new JsonArray();
        int barCount = getBarCount();
        for(int i=0;i<barCount; i++) {
            json.add(JsonUtil.object2json(getBar(i)));
        }
        return json;
    }

}
