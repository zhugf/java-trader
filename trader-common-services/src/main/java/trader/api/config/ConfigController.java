package trader.api.config;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.api.ControllerConstants;
import trader.common.config.ConfigService;
import trader.common.config.ConfigUtil;

/**
 * RESTful WebService for config
 */
@RestController
public class ConfigController {
    private final static Logger logger = LoggerFactory.getLogger(ConfigController.class);

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/config";

    @Autowired
    private ConfigService configService;

    private Map<String, String> configSources = new HashMap<>();

    public ConfigController(){
    }

    @PostConstruct
    public void init(){
        for(String s:configService.getSources()){
            configSources.put(s.toLowerCase(), s);
        }
    }

    @RequestMapping(path=URL_PREFIX+"/action/sourceChange",
            method=RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity doConfigSourceChange(@RequestBody String jsonStr)
    {
        JsonObject json = (JsonObject)(new JsonParser()).parse(jsonStr);
        String source = json.get("source").toString();
        if ( logger.isDebugEnabled() ){
            logger.debug("Config "+source+" changed, notified from RESTful service");
        }
        configService.sourceChange(source);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(path=URL_PREFIX+"/{configSource}/**",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<String> getConfigItem(@PathVariable(value="configSource") String configSourceStr, HttpServletRequest request){
        String requestURI = request.getRequestURI();
        String configItem = requestURI.substring( URL_PREFIX.length()+configSourceStr.length()+1);
        Object obj = null;
        if ( "ALL".equalsIgnoreCase(configSourceStr) ) {
            obj = ConfigUtil.getObject(configItem);
        }else{
            String configSource = configSources.get(configSourceStr.toLowerCase());
            if (configSource==null){
                return ResponseEntity.notFound().build();
            }
            obj = ConfigUtil.getObject(configSource, configItem);
        }
        if( logger.isDebugEnabled() ){
            logger.debug("Get config "+configSourceStr+" path \""+configItem+"\" value: \""+obj+"\"");
        }
        if ( obj==null ){
            return ResponseEntity.notFound().build();
        }else{
            return new ResponseEntity<String>(obj.toString(), HttpStatus.OK);
        }
    }

}
