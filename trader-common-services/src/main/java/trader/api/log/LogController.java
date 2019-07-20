package trader.api.log;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import trader.api.ControllerConstants;
import trader.service.log.LogLevelInfo;
import trader.service.log.LogService;

/**
 * RESTful WebService for Log
 */
@RestController
public class LogController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/log";

    @Autowired
    private LogService logService;

    @RequestMapping(path=URL_PREFIX+"/{category:.+}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LogLevelInfo getLogLevel( @PathVariable(value="category") String category){
        LogLevelInfo result = logService.getLevel(category);
        return result;
    }

    @RequestMapping(path=URL_PREFIX+"/{category:.+}",
            method= {RequestMethod.PUT, RequestMethod.POST} ,
            consumes = MediaType.TEXT_PLAIN_VALUE)
    public void setLogLevel(@PathVariable(value="category") String category, @RequestBody String levelStr){
        logService.setLevel(category, levelStr, true);
    }

}
