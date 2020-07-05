package trader.service.repository.jpa;

import trader.service.repository.AbsBOEntity;

public class JPABOEntity extends AbsBOEntity {

    public JPABOEntity(BOEntityType type) {
        super(type);
    }

    public Class<? extends AbsJPAEntity> getEntityClass() {
        switch(type) {
        case Order:
            return JPAOrderEntity.class;
        case Playbook:
            return JPAPlaybookEntity.class;
        case Transaction:
            return JPATransactionEntity.class;
        case Default:
            return JPADefaultEntity.class;
        }
        return null;
    }

    public AbsJPAEntity createEntityInstance() {
        AbsJPAEntity result = null;
        switch(type) {
        case Order:
            return new JPAOrderEntity();
        case Playbook:
            return new JPAPlaybookEntity();
        case Transaction:
            return new JPATransactionEntity();
        }
        return result;
    }

}
