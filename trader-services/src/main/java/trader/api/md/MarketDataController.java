package trader.api.md;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonArray;

import trader.api.ControllerConstants;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataService;

@RestController
public class MarketDataController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/md";

    @Autowired
    private MarketDataService marketDataService;

    @RequestMapping(path=URL_PREFIX+"/producers",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getProducers(){
        JsonArray jsonArray = new JsonArray();
        for(MarketDataProducer producer:marketDataService.getProducers()) {
            jsonArray.add(producer.toJsonObject());
        }
        return ResponseEntity.ok(jsonArray.toString());
    }

}
