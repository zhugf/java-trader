package trader.api.data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import trader.api.ControllerConstants;
import trader.common.util.StringUtil;
import trader.service.data.KVStoreIterator;
import trader.service.data.KVStoreService;

@RestController
public class StoreController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/data";

    @Autowired
    private KVStoreService kvStoreService;

    @RequestMapping(path=URL_PREFIX+"/store/key/",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getStoreKeys(){
        KVStoreIterator storeIterator = kvStoreService.getStore(null).iterator();
        JsonArray array = new JsonArray();
        while(storeIterator.hasNext()) {
            String key = storeIterator.next();
            if ( StringUtil.isEmpty(key)) {
                break;
            }
            array.add(key);
        }
        return ResponseEntity.ok(array.toString());
    }


    @RequestMapping(path=URL_PREFIX+"/store/key/{keyPrefix:.+}",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getStoreKey(@PathVariable(value="keyPrefix") String keyPrefix){
        KVStoreIterator storeIterator = kvStoreService.getStore(null).iterator();
        JsonObject json = new JsonObject();
        while(storeIterator.hasNext()) {
            String key = storeIterator.next();
            if ( StringUtil.isEmpty(key)) {
                break;
            }
            if ( key.startsWith(keyPrefix) ) {
                byte[] data = storeIterator.getValue();
                String data0 = "";
                if ( data!=null && data.length>0 ) {
                    try{
                        data0 = new String(data, StringUtil.UTF8);
                    }catch(Throwable t) {};
                }
                json.addProperty(key, data0);
            }
        }
        return ResponseEntity.ok(json.toString());
    }

}
