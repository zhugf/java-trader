package trader.api.tradlet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonArray;

import trader.api.ControllerConstants;
import trader.service.tradlet.TradeletService;
import trader.service.tradlet.TradletInfo;

@RestController
public class TradletController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/tradlet";

    @Autowired
    private TradeletService tradletService;

    @RequestMapping(path=URL_PREFIX+"/tradlet",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTradlets(){
        JsonArray array = new JsonArray();
        for(TradletInfo meta:tradletService.getTradletInfos()) {
            array.add(meta.toJson());
        }
        return ResponseEntity.ok(array.toString());
    }

    @RequestMapping(path=URL_PREFIX+"/tradletGroup",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTradletGroups(){
        JsonArray array = new JsonArray();
        for(TradletInfo meta:tradletService.getTradletInfos()) {
            array.add(meta.toJson());
        }
        return ResponseEntity.ok(array.toString());
    }


}
