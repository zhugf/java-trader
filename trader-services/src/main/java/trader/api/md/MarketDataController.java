package trader.api.md;

import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import trader.api.ControllerConstants;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonUtil;
import trader.common.util.WebResponse;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataService;

@RestController
public class MarketDataController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/md";

    @Autowired
    private MarketDataService marketDataService;

    @RequestMapping(path=URL_PREFIX+"/producer",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse getProducers(@RequestParam(name="pretty", required=false) boolean pretty){
        WebResponse response = new WebResponse(marketDataService.getProducers());
        return response;
    }

    @RequestMapping(path=URL_PREFIX+"/producer/{producerId}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse getProducer(@PathVariable(value="producerId") String producerId, @RequestParam(name="pretty", required=false) boolean pretty){
        MarketDataProducer producer = marketDataService.getProducer(producerId);
        if ( producer==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return new WebResponse(producer);
    }

    @RequestMapping(path=URL_PREFIX+"/subscriptions",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse getSubscriptions(@RequestParam(name="pretty", required=false) boolean pretty){
        return new WebResponse(marketDataService.getSubscriptions());
    }

    @RequestMapping(path=URL_PREFIX+"/primaryInstruments",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse getPrimaryInstruments(@RequestParam(name="pretty", required=false) boolean pretty){
        TreeSet<String> instruments = new TreeSet<>();
        for(Exchangeable i:marketDataService.getPrimaryInstruments()) {
            instruments.add(i.uniqueId());
        }
        return new WebResponse(instruments);
    }

    @RequestMapping(path=URL_PREFIX+"/last/{instrumentId}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse getInstrumentLastData(@PathVariable(value="instrumentId") String instrumentId, @RequestParam(name="pretty", required=false) boolean pretty){
        Exchangeable instrument = Exchangeable.fromString(instrumentId);
        MarketData md = marketDataService.getLastData(instrument);
        return new WebResponse(md);
    }

}
