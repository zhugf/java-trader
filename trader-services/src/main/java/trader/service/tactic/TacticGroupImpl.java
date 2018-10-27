package trader.service.tactic;

import java.util.List;
import java.util.Properties;

import trader.common.exchangeable.Exchangeable;
import trader.service.trade.AccountView;

/**
 * 策略组的实现类
 */
public class TacticGroupImpl implements TacticGroup {

    private String id;
    private boolean enabled;
    private AccountView accountView;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public AccountView getAccountView() {
        return accountView;
    }

    @Override
    public List<Exchangeable> getExchangeable() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Properties getProperties() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Tactic> getTactics() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        // TODO Auto-generated method stub
    }

}
