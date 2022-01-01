package trader.api.ta;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.ta4j.core.BarSeries;

import com.google.gson.JsonElement;

import trader.api.ControllerConstants;
import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.JsonUtil;
import trader.service.ta.BarAccess;
import trader.service.ta.BarService;

@RestController
public class TAController {
    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/ta";

    @Autowired
    private BarService technicalAnalysisService;

    @RequestMapping(path=URL_PREFIX+"/{instrument}",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public String getInstrumentDef(@PathVariable(value="instrument") String instrumentStr, @RequestParam(name="pretty", required=false) boolean pretty){
        Exchangeable instrument = Exchangeable.fromString(instrumentStr);
        BarAccess access = technicalAnalysisService.forInstrument(instrument);
        if ( access==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return (JsonUtil.json2str(access.toJson(), pretty));
    }

    @RequestMapping(path=URL_PREFIX+"/{instrument}/{level:.+}",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public String getLevelBars(@PathVariable(value="instrument") String instrumentStr, @PathVariable(value="level") String level, @RequestParam(name="pretty", required=false) boolean pretty){
        Exchangeable instrument = Exchangeable.fromString(instrumentStr);
        BarAccess access = technicalAnalysisService.forInstrument(instrument);
        if ( access==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        JsonElement json = null;
        PriceLevel l = PriceLevel.valueOf(level);
        BarSeries series = access.getSeries(l);
        if ( series==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        json = JsonUtil.object2json(series);
        return (JsonUtil.json2str(json, pretty));
    }

}
