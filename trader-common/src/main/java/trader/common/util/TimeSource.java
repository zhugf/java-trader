package trader.common.util;

import java.time.LocalDateTime;

@FunctionalInterface
public interface TimeSource {

    public LocalDateTime getTime();

}
