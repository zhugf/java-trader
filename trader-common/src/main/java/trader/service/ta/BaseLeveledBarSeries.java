package trader.service.ta;


import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.Num;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;

public class BaseLeveledBarSeries extends BaseBarSeries implements LeveledBarSeries, JsonEnabled {

    private static final long serialVersionUID = 2904300939512922674L;

    private Exchangeable instrument;

    private PriceLevel level;

    public BaseLeveledBarSeries(Exchangeable instrument, String name, PriceLevel level, Function<Number, Num> numFunction) {
        super(name, numFunction);
        this.level = level;
        this.instrument = instrument;
    }

    @Override
    public Exchangeable getInstrument() {
        return instrument;
    }

    @Override
    public PriceLevel getLevel() {
        return level;
    }

    public Bar getLastBar() {
        Bar result = null;
        int endIndex = getEndIndex();
        if ( endIndex>=0 ) {
            result = getBar(endIndex);
        }
        return result;
    }


    @Override
    public FutureBar getBar2(int i) {
        return (FutureBar)getBar(i);
    }

    public Bar removeLastBar() {
        if ( getBarCount()<=0 ) {
            return null;
        }
        Bar result = getLastBar();
        try {
            Field fldBars = getClass().getSuperclass().getDeclaredField("bars");
            fldBars.setAccessible(true);
            Field fldSeriesEndIndex = getClass().getSuperclass().getDeclaredField("seriesEndIndex");
            fldSeriesEndIndex.setAccessible(true);

            List<Bar> bars = (List<Bar>) fldBars.get(this);
            bars.remove(bars.size()-1);
            int val = fldSeriesEndIndex.getInt(this);
            fldSeriesEndIndex.setInt(this, val-1);
        }catch(Throwable t) {
            throw new RuntimeException(t);
        }
        return result;
    }

    @Override
    public BaseBarSeries getSubSeries(int startIndex, int endIndex){
        if(startIndex > endIndex){
            throw new IllegalArgumentException
                    (String.format("the endIndex: %s must be bigger than startIndex: %s", endIndex, startIndex));
        }
        BaseLeveledBarSeries result = new BaseLeveledBarSeries(instrument, getName(), level, numFunction);
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
        JsonObject json = new JsonObject();
        json.addProperty("instrument", instrument.uniqueId());
        json.addProperty("level", level.toString());
        json.addProperty("name", getName());
        JsonArray array = new JsonArray();
        int barCount = getBarCount();
        for(int i=0;i<barCount; i++) {
            array.add(JsonUtil.object2json(getBar(i)));
        }
        json.add("bars", array);
        return json;
    }

    public static BaseLeveledBarSeries fromJson(Function<Number, Num> numFunction, JsonElement jsonElem) {
        JsonObject json = jsonElem.getAsJsonObject();
        Exchangeable instrument = Exchangeable.fromString(json.get("instrument").getAsString());
        String name = JsonUtil.getProperty(json, "name", null);
        PriceLevel level = PriceLevel.valueOf(json.get("level").getAsString());

        if ( numFunction==null ) {
            numFunction = LongNum::valueOf;
        }
        BaseLeveledBarSeries result = new BaseLeveledBarSeries(instrument, name, level, numFunction);
        JsonArray bars = json.get("bars").getAsJsonArray();
        for(int i=0;i<bars.size();i++) {
            JsonElement barElem = bars.get(i);
            FutureBarImpl bar = FutureBarImpl.fromJson(instrument, barElem);
            result.addBar(bar);
        }
        return result;
    }

}
