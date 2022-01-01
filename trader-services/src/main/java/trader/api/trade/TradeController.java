package trader.api.trade;

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
import trader.common.util.JsonUtil;
import trader.common.util.WebResponse;
import trader.service.ServiceErrorConstants;
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
    public WebResponse getAccounts(@RequestParam(name="pretty", required=false) boolean pretty){
        return new WebResponse(tradeService.getAccounts());
    }

    @RequestMapping(path=URL_PREFIX+"/account/{accountId}",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse getAccount(@PathVariable(value="accountId") String accountId, @RequestParam(name="pretty", required=false) boolean pretty){
        Account account=tradeService.getAccount(accountId);
        if ( null==account) {
            return new WebResponse(ServiceErrorConstants.ERRCODE_TRADE_ACCOUNT_NOT_FOUND, "Account "+accountId+" is not found");
        }
        return new WebResponse(account);
    }

    @RequestMapping(path=URL_PREFIX+"/account/{accountId}/positions",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse getAccountPositions(@PathVariable(value="accountId") String accountId, @RequestParam(name="pretty", required=false) boolean pretty){
        Account account = tradeService.getAccount(accountId);
        if (null == account) {
            return new WebResponse(ServiceErrorConstants.ERRCODE_TRADE_ACCOUNT_NOT_FOUND, "Account "+accountId+" is not found");
        }
        return new WebResponse(account.getPositions());
    }

    @RequestMapping(path = URL_PREFIX + "/account/{accountId}/transactions",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse getAccountTransactions(@PathVariable(value = "accountId") String accountId, @RequestParam(name = "pretty", required = false) boolean pretty) {
        Account account = tradeService.getAccount(accountId);
        if (null == account) {
            return new WebResponse(ServiceErrorConstants.ERRCODE_TRADE_ACCOUNT_NOT_FOUND, "Account "+accountId+" is not found");
        }
        return new WebResponse(account.getTransactions());
    }

    @RequestMapping(path=URL_PREFIX+"/account/{accountId}/orders",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse getAccountOrders(@PathVariable(value="accountId") String accountId, @RequestParam(name="pretty", required=false) boolean pretty){
        Account account = tradeService.getAccount(accountId);
        if (null == account) {
            return new WebResponse(ServiceErrorConstants.ERRCODE_TRADE_ACCOUNT_NOT_FOUND, "Account "+accountId+" is not found");
        }
        return new WebResponse(account.getOrders());
    }

        @RequestMapping(path=URL_PREFIX+"/account/{accountId}/order/{orderRef}",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse getAccountOrder(@PathVariable(value="accountId") String accountId, @PathVariable(value="orderRef") String orderRef, @RequestParam(name="pretty", required=false) boolean pretty){
        Account account = tradeService.getAccount(accountId);
        if (null == account) {
            return new WebResponse(ServiceErrorConstants.ERRCODE_TRADE_ACCOUNT_NOT_FOUND, "Account "+accountId+" is not found");
        }
        Order order = account.getOrderByRef(orderRef);
        if ( order==null ) {
            return new WebResponse(ServiceErrorConstants.ERRCODE_TRADE_ORDER_NOT_FOUND, "Order "+orderRef+" is not found");
        }
        return new WebResponse(order);
    }

}
