package trader.service.tradlet;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.service.tradlet.TradletConstants.TradletGroupState;

public abstract class AbsTradletService implements TradletService{
    private static final Logger logger = LoggerFactory.getLogger(AbsTradletService.class);

    private List<TradletServiceListener> listeners = new ArrayList<>();


    public void addListener(TradletServiceListener listener) {
        if ( !listeners.add(listener)) {
            listeners.add(listener);
        }
    }

    protected void notifyGroupStateChanged(TradletGroup group, TradletGroupState oldState) {
        for(TradletServiceListener listener:listeners) {
            try{
                listener.onGroupStateChanged(group, oldState);
            }catch(Throwable t) {
                logger.error("Notify group "+group.getId()+" state change event failed", t);
            }
        }
    }

    protected void notifyPlaybookStateChanged(TradletGroup group, Playbook pb, PlaybookStateTuple oldStateTuple) {
        for(TradletServiceListener listener:listeners) {
            try{
                listener.onPlaybookStateChanged(group, pb, oldStateTuple);
            }catch(Throwable t) {
                logger.error("Notify group "+group.getId()+" state change event failed", t);
            }
        }
    }

}
