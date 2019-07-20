package trader.api.node;

import java.util.Collection;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.google.gson.JsonObject;

import trader.api.ControllerConstants;
import trader.common.beans.BeansContainer;
import trader.common.util.JsonUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.node.NodeInfo;
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

    @RequestMapping(path=URL_PREFIX+"/local",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String getLocalNode(){
        JsonObject json = TraderHomeUtil.toJson();
        json.addProperty("localId", nodeService.getLocalId());
        json.addProperty("connState", nodeService.getConnState().name());
        return json.toString();
    }

    @RequestMapping(path=URL_PREFIX,
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
            )
    public String getNodes(@RequestParam(name="activeOnly", required=false) boolean activeOnly, @RequestParam(name="pretty", required=false) boolean pretty)
    {
        if ( nodeMgmtService==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Collection<NodeInfo> nodes = nodeMgmtService.getNodes(activeOnly);
        return (JsonUtil.json2str(JsonUtil.object2json(nodes), pretty));
    }

}
