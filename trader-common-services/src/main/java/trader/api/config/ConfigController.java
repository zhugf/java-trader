package trader.api.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    public ConfigController(){
    }

    @PostConstruct
    public void init(){
    }

    @RequestMapping(path=URL_PREFIX+"/action/reload",
            method=RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public void doConfigSourceChange(@RequestBody String jsonStr)
    {
        JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
        String source = json.get("source").toString();
        if ( logger.isDebugEnabled() ){
            logger.debug("Config "+source+" changed, notified from RESTful service");
        }
        configService.reload(source);
    }

    @RequestMapping(path=URL_PREFIX+"/**",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String getConfigItem(HttpServletRequest request){
        String requestURI = request.getRequestURI();
        String configItem = requestURI.substring( URL_PREFIX.length()+1);
        Object obj = null;
        obj = ConfigUtil.getObject(configItem);
        if( logger.isDebugEnabled() ){
            logger.debug("Get config path \""+configItem+"\" value: \""+obj+"\"");
        }
        if ( obj==null ){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }else{
            return obj.toString();
        }
    }

}
