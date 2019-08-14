package trader.api.data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import trader.api.ControllerConstants;
import trader.common.util.StringUtil;
import trader.service.data.KVStore;
import trader.service.data.KVStoreIterator;
import trader.service.data.KVStoreService;

@RestController
public class StoreController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/data";

    @Autowired
    private KVStoreService kvStoreService;

    @RequestMapping(path=URL_PREFIX+"/store/keys/",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public String getStoreKeys(){
        KVStoreIterator storeIterator = kvStoreService.getStore(null).iterator();
        JsonArray array = new JsonArray();
        while(storeIterator.hasNext()) {
            String key = storeIterator.next();
            if ( StringUtil.isEmpty(key)) {
                break;
            }
            array.add(key);
        }
        return array.toString();
    }

    @RequestMapping(path=URL_PREFIX+"/store/keyContains/{keyPrefix:.+}",
    method=RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE)
    public String getStoreValuesByKeyPrefix(@PathVariable(value="keyPrefix") String keyPrefix){
        KVStoreIterator storeIterator = kvStoreService.getStore(null).iterator();
        JsonObject json = new JsonObject();
        while(storeIterator.hasNext()) {
            String key = storeIterator.next();
            if ( StringUtil.isEmpty(key)) {
                break;
            }
            if ( key.indexOf(keyPrefix)>=0 ) {
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
        return json.toString();
    }

    @RequestMapping(path=URL_PREFIX+"/store/key/{key:.+}",
    method=RequestMethod.GET,
    produces = MediaType.TEXT_PLAIN_VALUE)
    public String getValueByKey(@PathVariable(value="key") String key){
        byte[] data = kvStoreService.getStore(null).get(key);
        if ( data==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return new String(data, StringUtil.UTF8);
    }

    @RequestMapping(path=URL_PREFIX+"/store/key/{key:.+}",
    method=RequestMethod.DELETE,
    produces = MediaType.TEXT_PLAIN_VALUE)
    public String deleteValueByKey(@PathVariable(value="key") String key){
        KVStore kvStore = kvStoreService.getStore(null);
        String data = kvStore.getAsString(key);
        if ( data==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        kvStore.delete(key);
        return data;
    }

    @RequestMapping(path=URL_PREFIX+"/store/key/{key:.+}",
            method= {RequestMethod.PUT, RequestMethod.POST} ,
            consumes = MediaType.TEXT_PLAIN_VALUE)
    public void putValueByKey(@PathVariable(value="key") String key, @RequestBody String value){
        KVStore kvStore = kvStoreService.getStore(null);
        kvStore.put(key, value);
    }

}
