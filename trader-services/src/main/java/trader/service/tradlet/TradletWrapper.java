package trader.service.tradlet;

import trader.common.beans.BeansContainer;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletMetadata;

/**
 * 可动态切换实际实现类的策略包装类
 */
public class TradletWrapper implements Tradlet {
    private BeansContainer beansContainer;
    private Tradlet tactic;

    public TradletWrapper(Tradlet tactic) {
        this.tactic = tactic;
    }

    @Override
    public void init(BeansContainer beansContainer) throws Exception {
        this.beansContainer = beansContainer;
        tactic.init(beansContainer);
    }

    @Override
    public void destroy() {
        beansContainer = null;
        tactic.destroy();
    }

    @Override
    public TradletMetadata getMetadata() {
        return tactic.getMetadata();
    }

}
