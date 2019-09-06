package trader.api.ta;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import trader.api.ControllerConstants;
import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.service.ta.Bar2;
import trader.service.ta.TechnicalAnalysisAccess;
import trader.service.ta.TechnicalAnalysisService;

@RestController
public class TAController {
    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/ta";

    @Autowired
    private TechnicalAnalysisService taService;

    @RequestMapping(path=URL_PREFIX+"/{instrument}/{level}/",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public String getBars(@PathVariable(value="instrument") String instrument, @PathVariable(value="level") String level){
        Exchangeable e = Exchangeable.fromString(instrument);
        PriceLevel l = PriceLevel.valueOf(level);
        TechnicalAnalysisAccess item = taService.forInstrument(e);
        TimeSeries series = null;
        if ( item!=null ) {
            series = item.getSeries(l);
        }
        if ( series==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        JsonArray array = new JsonArray();
        for(int i=0;i<series.getBarCount();i++) {
            Bar bar = series.getBar(i);
            array.add(bar2json(bar));
        }
        return array.toString();
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
        if ( bar instanceof Bar2) {
            json.addProperty("openInt", ((Bar2)bar).getOpenInterest());
        }
        return json;
    }
}
