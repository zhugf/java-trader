package trader;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import trader.common.config.XMLConfigProvider;
import trader.common.util.TraderHomeUtil;
import trader.service.config.ConfigServiceImpl;

@SpringBootApplication
public class TraderMain {

    public static void main(String[] args) throws Throwable
    {
        File traderHome = TraderHomeUtil.getTraderHome();
        System.out.println("Starting trader from home " +traderHome);
        initServices();
        ConfigurableApplicationContext context = SpringApplication.run(TraderMain.class, args);
    }

    private static void initServices() throws Exception {
        File traderHome = TraderHomeUtil.getTraderHome();
        ConfigServiceImpl.staticRegisterProvider("TRADER", new XMLConfigProvider(new File(traderHome,"etc/trader.xml")));
    }

}
