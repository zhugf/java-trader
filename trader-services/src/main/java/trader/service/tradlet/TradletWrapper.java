package trader.service.tradlet;

import trader.common.beans.BeansContainer;

/**
 * 可动态切换实际实现类的策略包装类
 */
public class TradletWrapper implements Tradlet {
    private BeansContainer beansContainer;
    private Tradlet tradlet;

    public TradletWrapper(Tradlet tactic) {
        this.tradlet = tactic;
    }

    @Override
    public void init(BeansContainer beansContainer) throws Exception {
        this.beansContainer = beansContainer;
        tradlet.init(beansContainer);
    }

    @Override
    public void destroy() {
        beansContainer = null;
        tradlet.destroy();
    }

    @Override
    public TradletMetadata getMetadata() {
        return tradlet.getMetadata();
    }

    public void setDelegate(Tradlet tradlet) {
        this.tradlet = tradlet;
    }

}
