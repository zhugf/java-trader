package trader.api.tradlet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<String> getTradlets(){
        return ResponseEntity.ok(JsonUtil.object2json(tradletService.getTradletInfos()).toString());
    }

    @RequestMapping(path=URL_PREFIX+"/group",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTradletGroups(){
        return ResponseEntity.ok(JsonUtil.object2json(tradletService.getGroups()).toString());
    }

    @RequestMapping(path=URL_PREFIX+"/group/{groupId}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTradletGroup(@PathVariable(value="groupId") String groupId, @RequestParam(name="pretty", required=false) boolean pretty){
        TradletGroup g = null;
        for(TradletGroup group:tradletService.getGroups()) {
            if ( StringUtil.equalsIgnoreCase(groupId, group.getId()) ) {
                g = group;
                break;
            }
        }
        if ( g==null ) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(g.toJson().toString());
    }

    @RequestMapping(path=URL_PREFIX+"/reload",
        method=RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reload() throws Exception
    {
        return ResponseEntity.ok(JsonUtil.object2json(tradletService.reloadGroups()).toString());
    }

}
