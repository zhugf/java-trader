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
import trader.common.util.JsonUtil;
import trader.service.trade.Account;
import trader.service.trade.Order;
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
        return ResponseEntity.ok(JsonUtil.object2json(tradeService.getAccounts()).toString());
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
        return ResponseEntity.ok(JsonUtil.object2json(account.getPositions()).toString());
    }

        @RequestMapping(path=URL_PREFIX+"/account/{accountId}/orders",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAccountOrders(@PathVariable(value="accountId") String accountId){
        Account account = tradeService.getAccount(accountId);
        if (null == account) {
            return ResponseEntity.notFound().build();
        }
        JsonArray array = new JsonArray();
        for(Order order: account.getOrders()) {
            array.add(order.getRef());
        }
        return ResponseEntity.ok(array.toString());
    }

        @RequestMapping(path=URL_PREFIX+"/account/{accountId}/order/{orderRef}",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAccountOrder(@PathVariable(value="accountId") String accountId, @PathVariable(value="orderRef") String orderRef){
        Account account = tradeService.getAccount(accountId);
        if (null == account) {
            return ResponseEntity.notFound().build();
        }
        Order order = account.getOrder(orderRef);
        if ( order==null ) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order.toJson().toString());
    }

}
