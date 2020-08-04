package trader.api.logon;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonObject;

import trader.api.ControllerConstants;
import trader.common.util.JsonUtil;

@RestController
public class LogonController {
    private final static Logger logger = LoggerFactory.getLogger(LogonController.class);

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/logon";


    @RequestMapping(path=URL_PREFIX+"/info",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String getUserInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();

        Object result = null;

        if ( principal instanceof UserDetails ) {
            String username = ((UserDetails)principal).getUsername();
            List<String> roles = new ArrayList<>();

            for(GrantedAuthority authority:((UserDetails)principal).getAuthorities()) {
                roles.add( authority.getAuthority());
            }
            JsonObject json = new JsonObject();
            json.addProperty("user", username);
            json.add("roles", JsonUtil.object2json(roles));
            result = json;
        } else {
            result = principal;
        }
        return JsonUtil.object2json(result).toString();
    }

}
