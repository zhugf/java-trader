package trader.api.tradlet;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import trader.api.ControllerConstants;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.tradlet.TradletGroup;
import trader.service.tradlet.TradletService;

@RestController
public class TradletController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/tradletService";

    @Autowired
    private TradletService tradletService;

    @RequestMapping(path=URL_PREFIX+"/tradlet",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTradlets(@RequestParam(name="pretty", required=false) boolean pretty){
        return ResponseEntity.ok(JsonUtil.json2str(JsonUtil.object2json(tradletService.getTradletInfos()), pretty));
    }

    @RequestMapping(path=URL_PREFIX+"/group",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public String getTradletGroups(@RequestParam(name="pretty", required=false) boolean pretty){
        return JsonUtil.json2str(JsonUtil.object2json(tradletService.getGroups()), pretty);
    }

    @RequestMapping(path=URL_PREFIX+"/group/{groupId}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String getTradletGroup(@PathVariable(value="groupId") String groupId, @RequestParam(name="pretty", required=false) boolean pretty){
        TradletGroup g = null;
        for(TradletGroup group:tradletService.getGroups()) {
            if ( StringUtil.equalsIgnoreCase(groupId, group.getId()) ) {
                g = group;
                break;
            }
        }
        if ( g==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return JsonUtil.json2str(g.toJson(), pretty);
    }

    @RequestMapping(path=URL_PREFIX+"/group/{groupId}/queryData",
            method=RequestMethod.POST,
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String tradletGroupQueryData(@PathVariable(value="groupId") String groupId, @RequestBody String queryExpr){
        TradletGroup g = null;
        for(TradletGroup group:tradletService.getGroups()) {
            if ( StringUtil.equalsIgnoreCase(groupId, group.getId()) ) {
                g = group;
                break;
            }
        }
        if ( g==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return g.queryData(queryExpr);
    }

    @RequestMapping(path=URL_PREFIX+"/reload",
        method=RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public String reload() throws Exception
    {
        return JsonUtil.object2json(tradletService.reloadGroups()).toString();
    }

}
