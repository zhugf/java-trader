package trader.api.tradlet;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import trader.api.ControllerConstants;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.tradlet.TradletGroup;
import trader.service.tradlet.TradletService;

@RestController
public class TradletController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/tradletService";

    @Autowired
    private TradletService tradletService;

    @PostConstruct
    public void init() {

    }

    @GetMapping(path=URL_PREFIX+"/tradlet",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTradlets(@RequestParam(name="pretty", required=false) boolean pretty){
        return ResponseEntity.ok(JsonUtil.json2str(JsonUtil.object2json(tradletService.getTradletInfos()), pretty));
    }

    @GetMapping(path=URL_PREFIX+"/group",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public String getTradletGroups(@RequestParam(name="pretty", required=false) boolean pretty){
        return JsonUtil.json2str(JsonUtil.object2json(tradletService.getGroups()), pretty);
    }

    @GetMapping(path=URL_PREFIX+"/group/{groupId}",
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

    @GetMapping(path=URL_PREFIX+"/group/{groupId}/{path:.+}",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> tradletGroupGetRequest(HttpServletRequest request, @PathVariable(value="groupId") String groupId, @PathVariable(value="path") String path){
        TradletGroup g = tradletService.getGroup(groupId);
        if ( null==g ) {
            return ResponseEntity.notFound().build();
        }
        Map<String, String> params = getRequestParams(request);
        Object r = g.onRequest(path, params, null);
        if ( null==r ) {
            return ResponseEntity.notFound().build();
        }
        if ( ConversionUtil.toBoolean(params.get("pretty")) ) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            r = gson.toJson(JsonUtil.object2json(r));
        }
        return ResponseEntity.ok(r.toString());
    }

    @PostMapping(path=URL_PREFIX+"/group/{groupId}/{path:.+}",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> tradletGroupPostRequest(HttpServletRequest request, @PathVariable(value="groupId") String groupId, @PathVariable(value="path") String path, @RequestBody String payload){
        TradletGroup g = tradletService.getGroup(groupId);
        if ( null==g ) {
            return ResponseEntity.notFound().build();
        }
        Map<String, String> params = getRequestParams(request);
        Object r = g.onRequest(path, params, payload);
        if ( null==r ) {
            return ResponseEntity.notFound().build();
        }
        if ( ConversionUtil.toBoolean(params.get("pretty")) ) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            r = gson.toJson(JsonUtil.object2json(r));
        }
        return ResponseEntity.ok(r.toString());
    }

    @RequestMapping(path=URL_PREFIX+"/reload",
        method=RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public String reload() throws Exception
    {
        return JsonUtil.object2json(tradletService.reloadGroups()).toString();
    }

    private Map<String, String> getRequestParams(HttpServletRequest request){
        Map<String, String> result = new HashMap<>();
        for(String pname:Collections.list(request.getParameterNames())) {
            result.put(pname, request.getParameter(pname));
        }
        return result;
    }

}
