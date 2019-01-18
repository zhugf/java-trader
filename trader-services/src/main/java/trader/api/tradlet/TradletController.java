package trader.api.tradlet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import trader.api.ControllerConstants;
import trader.common.util.JsonUtil;
import trader.service.tradlet.TradletService;

@RestController
public class TradletController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/tradlet";

    @Autowired
    private TradletService tradletService;

    @RequestMapping(path=URL_PREFIX+"/tradlet",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTradlets(){
        return ResponseEntity.ok(JsonUtil.object2json(tradletService.getTradletInfos()).toString());
    }

    @RequestMapping(path=URL_PREFIX+"/tradletGroup",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTradletGroups(){
        return ResponseEntity.ok(JsonUtil.object2json(tradletService.getGroups()).toString());
    }

    @RequestMapping(path=URL_PREFIX+"/playbookTemplate",
        method=RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPlaybookTemplates(){
        return ResponseEntity.ok(JsonUtil.object2json(tradletService.getPlaybookTemplates()).toString());
    }

    @RequestMapping(path=URL_PREFIX+"/reload",
        method=RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reload() throws Exception
    {
        return ResponseEntity.ok(JsonUtil.object2json(tradletService.reloadGroups()).toString());
    }

}
