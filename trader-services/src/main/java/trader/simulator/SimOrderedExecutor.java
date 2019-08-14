package trader.simulator;

import trader.service.concurrent.OrderedExecutor;

public class SimOrderedExecutor implements OrderedExecutor {

    @Override
    public void execute(String key, Runnable cmd) {
        cmd.run();
    }

}
