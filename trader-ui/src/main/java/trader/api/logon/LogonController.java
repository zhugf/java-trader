package trader.api.logon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

import trader.api.ControllerConstants;;

@RestController
public class LogonController {
    private final static Logger logger = LoggerFactory.getLogger(LogonController.class);

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/logon";


}
