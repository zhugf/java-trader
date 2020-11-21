package trader.api.node;

import java.util.Collection;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import trader.api.ControllerConstants;
import trader.common.beans.BeansContainer;
import trader.common.util.JsonUtil;
import trader.service.node.NodeClientChannel;
import trader.service.node.NodeSession;
import trader.service.node.NodeService;

/**
 * RESTful WebService for Node
 */
@RestController
public class NodeController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/node";

    @Autowired
    private BeansContainer beansContainer;

    private NodeClientChannel nodeClientChannel;

    private NodeService nodeSessionService;

    @PostConstruct
    public void init() {
        nodeClientChannel = beansContainer.getBean(NodeClientChannel.class);
        nodeSessionService = beansContainer.getBean(NodeService.class);
    }

    @RequestMapping(path=URL_PREFIX+"/session",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE
            )
    public String getNodeSessions(@RequestParam(name="pretty", required=false) boolean pretty)
    {
        if ( nodeSessionService==null ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Collection<NodeSession> sessions = nodeSessionService.getNodeSessions();
        return (JsonUtil.json2str(JsonUtil.object2json(sessions), pretty));
    }

    @RequestMapping(path=URL_PREFIX+"/session/{sessionId}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE
            )
    public String getNodeSession(@PathVariable(value="sessionId") String sessionId, @RequestParam(name="pretty", required=false) boolean pretty)
    {
        NodeSession session = null;
        if ( nodeSessionService!=null ) {
            session = nodeSessionService.getSession(sessionId);
        }
        if ( null==session ) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return (JsonUtil.json2str(JsonUtil.object2json(session), pretty));
    }

}
