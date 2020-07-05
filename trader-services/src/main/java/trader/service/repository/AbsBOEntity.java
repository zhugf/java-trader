package trader.service.repository;

import trader.common.util.UUIDUtil;

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

}
