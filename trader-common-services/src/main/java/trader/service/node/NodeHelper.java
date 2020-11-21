package trader.service.node;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;

public class NodeHelper {
    private static final Logger logger = LoggerFactory.getLogger(NodeHelper.class);

    /**
     * 根据path找到并发现REST Controller
     */
    public static NodeMessage controllerInvoke(RequestMappingHandlerMapping requestMappingHandlerMapping, NodeMessage reqMessage) {
        String path = ConversionUtil.toString(reqMessage.getField(NodeMessage.FIELD_PATH));
        //匹配合适的
        Object result = null;
        Throwable t = null;
        RequestMappingInfo reqMappingInfo=null;
        HandlerMethod reqHandlerMethod = null;
        Map<RequestMappingInfo, HandlerMethod> map = requestMappingHandlerMapping.getHandlerMethods();
        for(RequestMappingInfo info:map.keySet()) {
            List<String> matches = info.getPatternsCondition().getMatchingPatterns(path);
            if ( matches.isEmpty() ) {
                continue;
            }
            reqMappingInfo = info;
            reqHandlerMethod = map.get(info);
            break;
        }

        if ( reqMappingInfo==null ) {
            t = new Exception("Controller for "+path+" is not found");
            logger.error("Controller for "+path+" is not found");
        } else {
            MethodParameter[] methodParams = reqHandlerMethod.getMethodParameters();
            Object[] params = new Object[methodParams.length];
            try{
                for(int i=0;i<methodParams.length;i++) {
                    MethodParameter param = methodParams[i];
                    String paramName = param.getParameterName();
                    params[i] = ConversionUtil.toType(param.getParameter().getType(), reqMessage.getField(paramName));
                    if ( params[i]==null && !param.isOptional() ) {
                        throw new IllegalArgumentException("Method parameter "+paramName+" is missing");
                    }
                }
                result = reqHandlerMethod.getMethod().invoke(reqHandlerMethod.getBean(), params);
            }catch(Throwable ex ) {
                if ( ex instanceof InvocationTargetException ) {
                    t = ((InvocationTargetException)ex).getTargetException();
                }else {
                    t = ex;
                }
                logger.error("Invoke controller "+path+" with params "+Arrays.asList(params)+" failed: "+t, t);
            }
        }
        NodeMessage respMessage = reqMessage.createResponse();
        if ( t!=null ) {
            respMessage.setErrCode(-1);
            respMessage.setErrMsg(t.toString());
        } else {
            respMessage.setField(NodeMessage.FIELD_RESULT, JsonUtil.object2json(result));
        }
        return respMessage;
    }

}
