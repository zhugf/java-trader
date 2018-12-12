package trader.api.md;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
    public ResponseEntity<String> getProducers(){
        return ResponseEntity.ok(JsonUtil.object2json(marketDataService.getProducers()).toString());
    }

    @RequestMapping(path=URL_PREFIX+"/producer/{producerId}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getProducer(@PathVariable(value="producerId") String producerId){

        MarketDataProducer producer = marketDataService.getProducer(producerId);
        if ( producer==null ) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(producer.toJson().toString());
    }

    @RequestMapping(path=URL_PREFIX+"/subscriptions",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSubscriptions(){
        return ResponseEntity.ok(JsonUtil.object2json(marketDataService.getSubscriptions()).toString());
    }

    @RequestMapping(path=URL_PREFIX+"/{exchangeableId}/lastData",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getExchangeableLastData(@PathVariable(value="exchangeableId") String exchangeableId){
        Exchangeable e = Exchangeable.fromString(exchangeableId);
        MarketData md = marketDataService.getLastData(e);
        return ResponseEntity.ok(md.toJson().toString());
    }

}
