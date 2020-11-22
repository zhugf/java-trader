package trader.service.tradlet;

import trader.service.tradlet.TradletConstants.TradletGroupState;

public interface TradletServiceListener {

    public void onGroupStateChanged(TradletGroup group, TradletGroupState oldState);

    public void onPlaybookStateChanged(TradletGroup group, Playbook pb, PlaybookStateTuple oldStateTuple);

}
