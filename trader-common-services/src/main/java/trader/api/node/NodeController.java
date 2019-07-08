package trader.api.node;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import trader.api.ControllerConstants;
import trader.common.util.TraderHomeUtil;

/**
 * RESTful WebService for Node
 */
@RestController
public class NodeController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/node";

    @RequestMapping(path=URL_PREFIX+"/info",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getNodeInfo(){
        return ResponseEntity.ok(TraderHomeUtil.toJson().toString());
    }

}
