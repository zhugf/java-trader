package trader.service.stats;

import java.io.IOException;
import java.util.List;

/**
 * Abstract endpoint of statistics item value publish:
 * <LI>Local
 * <LI>Http
 * <LI>MQ/kafka
 */
@FunctionalInterface
public interface StatsPublishEndpoint {

    public void publish(List<StatsItemPublishEvent> events) throws IOException;

}
