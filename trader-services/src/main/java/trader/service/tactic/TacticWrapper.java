package trader.service.tactic;

import trader.common.beans.BeansContainer;

/**
 * 可动态切换实际实现类的策略包装类
 */
public class TacticWrapper implements Tactic {
    private BeansContainer beansContainer;
    private Tactic tactic;

    public TacticWrapper(Tactic tactic) {
        this.tactic = tactic;
    }

    @Override
    public void init(BeansContainer beansContainer) throws Exception {
        this.beansContainer = beansContainer;
        tactic.init(beansContainer);
    }

    @Override
    public void destory() {
        beansContainer = null;
        tactic.destory();
    }

    @Override
    public TacticMetadata getMetadata() {
        return tactic.getMetadata();
    }

}
