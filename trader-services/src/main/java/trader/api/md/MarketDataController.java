package trader.api.md;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import trader.api.ControllerConstants;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonUtil;
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
    public ResponseEntity<String> getProducers(@RequestParam(name="pretty", required=false) boolean pretty){
        return ResponseEntity.ok(JsonUtil.json2str(JsonUtil.object2json(marketDataService.getProducers()), pretty));
    }

    @RequestMapping(path=URL_PREFIX+"/producer/{producerId}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getProducer(@PathVariable(value="producerId") String producerId, @RequestParam("pretty") boolean pretty){

        MarketDataProducer producer = marketDataService.getProducer(producerId);
        if ( producer==null ) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(JsonUtil.json2str(producer.toJson(), pretty));
    }

    @RequestMapping(path=URL_PREFIX+"/subscriptions",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSubscriptions(@RequestParam(name="pretty", required=false) boolean pretty){
        return ResponseEntity.ok(JsonUtil.json2str(JsonUtil.object2json(marketDataService.getSubscriptions()), pretty));
    }

    @RequestMapping(path=URL_PREFIX+"/last/{exchangeableId}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getExchangeableLastData(@PathVariable(value="exchangeableId") String exchangeableId, @RequestParam(name="pretty", required=false) boolean pretty){
        Exchangeable e = Exchangeable.fromString(exchangeableId);
        MarketData md = marketDataService.getLastData(e);
        return ResponseEntity.ok(JsonUtil.json2str(md.toJson(), pretty));
    }

}
