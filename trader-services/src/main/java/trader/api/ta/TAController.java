package trader.api.ta;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import trader.api.ControllerConstants;
import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.service.ta.FutureBar;
import trader.service.ta.TAService;

@RestController
public class TAController {
    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/ta";

    @Autowired
    private TAService taService;

    @RequestMapping(path=URL_PREFIX+"/{exchangeable}/{level}/",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getBars(@PathVariable(value="exchangeable") String exchangeable, @PathVariable(value="level") String level){
        Exchangeable e = Exchangeable.fromString(exchangeable);
        PriceLevel l = ConversionUtil.toEnum(PriceLevel.class, level);
        TimeSeries series = taService.getSeries(e, l);
        if ( series==null ) {
            return ResponseEntity.notFound().build();
        }
        JsonArray array = new JsonArray();
        for(int i=0;i<series.getBarCount();i++) {
            Bar bar = series.getBar(i);
            array.add(bar2json(bar));
        }
        return ResponseEntity.ok(array.toString());
    }

    private static JsonObject bar2json(Bar bar) {
        JsonObject json = new JsonObject();
        json.addProperty("beginTime", DateUtil.date2str(bar.getBeginTime().toLocalDateTime()));
        json.addProperty("beginTimestamp", bar.getBeginTime().toInstant().toEpochMilli() );
        json.addProperty("endTime", DateUtil.date2str(bar.getEndTime().toLocalDateTime()));
        json.addProperty("endTimestamp", bar.getEndTime().toInstant().toEpochMilli() );
        json.addProperty("open", PriceUtil.long2str(bar.getOpenPrice().longValue()));
        json.addProperty("max", PriceUtil.long2str(bar.getMaxPrice().longValue()));
        json.addProperty("min", PriceUtil.long2str(bar.getMinPrice().longValue()));
        json.addProperty("close", PriceUtil.long2str(bar.getClosePrice().longValue()));
        json.addProperty("amount", PriceUtil.long2str(bar.getAmount().longValue()));

        json.addProperty("volume", bar.getVolume().longValue());
        if ( bar instanceof FutureBar) {
            json.addProperty("openInt", ((FutureBar)bar).getOpenInterest().longValue());
        }
        return json;
    }
}
