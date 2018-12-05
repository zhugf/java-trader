package trader.api.trade;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonArray;

import trader.api.ControllerConstants;
import trader.service.trade.Account;
import trader.service.trade.Position;
import trader.service.trade.TradeService;

@RestController
public class TradeController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/trade";

    @Autowired
    private TradeService tradeService;

    @RequestMapping(path=URL_PREFIX+"/account",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAccounts(){
        JsonArray jsonArray = new JsonArray();
        for(Account acount:tradeService.getAccounts()) {
            jsonArray.add(acount.toJson());
        }
        return ResponseEntity.ok(jsonArray.toString());
    }

    @RequestMapping(path=URL_PREFIX+"/account/{accountId}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAccount(@PathVariable(value="accountId") String accountId){

        Account account=tradeService.getAccount(accountId);
        if ( null==account) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(account.toJson().toString());
    }

        @RequestMapping(path=URL_PREFIX+"/account/{accountId}/positions",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAccountPositions(@PathVariable(value="accountId") String accountId){
        Account account = tradeService.getAccount(accountId);
        if (null == account) {
            return ResponseEntity.notFound().build();
        }
        JsonArray array = new JsonArray();
        for(Position pos: account.getPositions(null)) {
            array.add(pos.toJson());
        }
        return ResponseEntity.ok(array.toString());
    }

}
