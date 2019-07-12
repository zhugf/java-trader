package trader.api.node;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonObject;

import trader.api.ControllerConstants;
import trader.common.beans.BeansContainer;
import trader.common.util.TraderHomeUtil;
import trader.service.node.NodeMgmtService;
import trader.service.node.NodeService;

/**
 * RESTful WebService for Node
 */
@RestController
public class NodeController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/node";

    @Autowired
    private BeansContainer beansContainer;

    private NodeService nodeService;

    private NodeMgmtService nodeMgmtService;

    @PostConstruct
    public void init() {
        nodeService = beansContainer.getBean(NodeService.class);
        nodeMgmtService = beansContainer.getBean(NodeMgmtService.class);
    }

    @RequestMapping(path=URL_PREFIX+"/info",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getNodeInfo(){
        JsonObject json = TraderHomeUtil.toJson();
        json.addProperty("localId", nodeService.getLocalId());
        json.addProperty("connState", nodeService.getConnState().name());
        return ResponseEntity.ok(json.toString());
    }

}
