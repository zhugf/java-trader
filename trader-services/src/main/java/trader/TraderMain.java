package trader;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import trader.common.util.TraderHomeUtil;

@SpringBootApplication
public class TraderMain {

    public static void main(String[] args) {
        File traderHome = TraderHomeUtil.getTraderHome();
        ConfigurableApplicationContext context = SpringApplication.run(TraderMain.class, args);
    }

}
