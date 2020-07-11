package trader.service.repository;

import trader.common.util.UUIDUtil;
import trader.service.trade.OrderImpl;
import trader.service.trade.TransactionImpl;
import trader.service.tradlet.PlaybookImpl;

public abstract class AbsBOEntity implements BOEntity {
    protected BOEntityType type;
    protected String idPrefix;

    public AbsBOEntity(BOEntityType type) {
        this.type = type;
        switch(type) {
        case Order:
            idPrefix = BOEntity.ID_PREFIX_ORDER;
            break;
        case Playbook:
            idPrefix = BOEntity.ID_PREFIX_PLAYBOOK;
            break;
        case Transaction:
            idPrefix = BOEntity.ID_PREFIX_TRANSACTION;
            break;
        }
    }

    public BOEntityType getType() {
        return type;
    }

    public String getIdPrefix() {
        return idPrefix;
    }

    @Override
    public String genId() {
        return idPrefix+"_"+UUIDUtil.genUUID58();
    }

    public Object data2entity(BORepository repository, String id, String data) {
        switch(type) {
        case Order:
            return OrderImpl.load(repository, id, data);
        case Transaction:
            return TransactionImpl.load(repository, id, data);
        case Playbook:
            return PlaybookImpl.load(repository, id, data);
        }
        return null;
    }

}
