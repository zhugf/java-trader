package trader.service.tradlet;

/**
 * 交易策略分组的单线程引擎, 每个对象必须独占一个线程
 */
public class TradletGroupThreadedEngine {
    private TradletGroupImpl group;

    public TradletGroupThreadedEngine(TradletGroupImpl group) {
        this.group = group;
    }

    public TradletGroupImpl getGroup() {
        return group;
    }

}
