package trader.api.ta;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.ta4j.core.TimeSeries;

import com.google.gson.JsonElement;

import trader.api.ControllerConstants;
import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.ta.TechnicalAnalysisAccess;
import trader.service.ta.TechnicalAnalysisService;
import trader.service.ta.trend.WaveBar;
import trader.service.ta.trend.WaveBar.WaveType;

@RestController
public class TAController {
    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/ta";

    @Autowired
    private TechnicalAnalysisService technicalAnalysisService;

    @RequestMapping(path=URL_PREFIX+"/{instrument}",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public String getInstrumentDef(@PathVariable(value="instrument") String instrumentStr, @RequestParam(name="pretty", required=false) boolean pretty){
        Exchangeable instrument = Exchangeable.fromString(instrumentStr);
        TechnicalAnalysisAccess access = technicalAnalysisService.forInstrument(instrument);
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
        TechnicalAnalysisAccess access = technicalAnalysisService.forInstrument(instrument);
        if ( access==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        JsonElement json = null;
        String[] levelAndWaveTypes = StringUtil.split(level, "\\.");
        PriceLevel l = PriceLevel.valueOf(levelAndWaveTypes[0]);
        if ( levelAndWaveTypes.length==1 ) {
            TimeSeries series = access.getSeries(l);
            json = JsonUtil.object2json(series);
        } else {
            WaveType waveType = ConversionUtil.toEnum(WaveType.class, levelAndWaveTypes[1]);
            List<WaveBar> bars = access.getWaveBars(l, waveType);
            json = JsonUtil.object2json(bars);
        }
        return (JsonUtil.json2str(json, pretty));
    }

}
