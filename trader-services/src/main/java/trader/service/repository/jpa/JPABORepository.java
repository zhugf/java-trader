package trader.service.repository.jpa;

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.google.gson.JsonElement;

import trader.common.util.StringUtil;
import trader.common.util.concurrent.DelegateExecutor;
import trader.service.repository.BOEntity;
import trader.service.repository.BOEntityIterator;
import trader.service.repository.BORepository;

/**
 * SpringJPA Repository
 */
@Service
public class JPABORepository implements BORepository {
    private static final Logger logger = LoggerFactory.getLogger(JPABORepository.class);

    @Autowired
    private ExecutorService executorService;

    private DelegateExecutor asyncExecutor;

    @Autowired
    private PlatformTransactionManager tm;

    @Autowired
    private EntityManager em;

    private ThreadLocal<TransactionStatus> currTxnStatus = new ThreadLocal<>();

    private JPABOEntity[] entities = null;

    @PostConstruct
    public void init() {
        asyncExecutor = new DelegateExecutor(executorService, 1);
        initEntities();
    }

    @PreDestroy
    public void destroy() {

    }

    @Override
    public BOEntity getBOEntity(BOEntityType entityType) {
        return entities[entityType.ordinal()];
    }

    @Transactional
    @Override
    public String load(BOEntityType entityType, String entityId) {
        Class entityClass = entities[entityType.ordinal()].getEntityClass();
        AbsJPAEntity entity = (AbsJPAEntity)em.find(entityClass, entityId);
        String result = null;
        if ( entity!=null ) {
            result = entity.getAttrs();
        }
        return result;
    }

    @Override
    public BOEntityIterator search(BOEntityType entityType, String queryExpr) {
        Class entityClass = entities[entityType.ordinal()].getEntityClass();
        StringBuilder query = new StringBuilder(128);
        query.append("SELECT a FROM ").append(entityClass.getSimpleName()).append(" a ");
        if ( !StringUtil.isEmpty(queryExpr)){
            query.append("WHERE ").append(queryExpr);
        }
        List<? extends AbsJPAEntity> result = em.createQuery(query.toString(), entityClass).getResultList();
        return new JPAEntityIterator(result.iterator());
    }

    @Override
    public void asynSave(BOEntityType entityType, String id, JsonElement value) {
        asyncExecutor.execute(()->{
            try{
                save(entityType, id, value);
            }catch(Throwable t) {
                logger.error("asyncSave failed", t);
            }
        });
    }

    @Transactional
    @Override
    public void save(BOEntityType entityType, String id, JsonElement value) {
        AbsJPAEntity entityInstance = entities[entityType.ordinal()].createEntityInstance();
        entityInstance.setId(id);
        entityInstance.setAttrs(value);
        try{
            entityInstance = em.merge(entityInstance);
        }catch(Exception e) {
            em.persist(entityInstance);
        }
    }

    public void beginTransaction(boolean readOnly){
        TransactionStatus txnStatus = currTxnStatus.get();
        if ( txnStatus!=null ){
            if (txnStatus.isCompleted()) { //当前事务已结束
                currTxnStatus.remove();
                txnStatus = null;
            } else if ( txnStatus.isRollbackOnly()!=readOnly ){ //当前事务与要开始的事务不一致
                throw new RuntimeException("Unable to start new transaction with out commit/rollback current");
            } else { //完全一致, do nothing
                if ( logger.isDebugEnabled() ) {
                    logger.debug("beginTransaction("+readOnly+") is the same with current txn, do nothing");
                }
                return;
            }
        }else {
            DefaultTransactionDefinition txnDef = new DefaultTransactionDefinition();
            txnDef.setReadOnly(readOnly);
            txnStatus = tm.getTransaction(txnDef);
            currTxnStatus.set(txnStatus);
            if ( logger.isDebugEnabled() ) {
                logger.debug("beginTransaction("+readOnly+")");
            }
        }
    }

    public boolean endTransaction(boolean commit){
        if ( currTxnStatus.get()==null ){
            return false;
        }
        TransactionStatus txnStatus = currTxnStatus.get();
        if ( logger.isDebugEnabled() ) {
            logger.debug("endTransaction("+commit+") on current(rollbackOnly="+txnStatus.isRollbackOnly()+")");
        }
        currTxnStatus.remove();
        if ( commit ){
            tm.commit(txnStatus);
        }else{
            tm.rollback(txnStatus);
        }
        return true;
    }

    public boolean inTransaction() {
        return currTxnStatus.get()!=null;
    }

    private void initEntities() {
        entities = new JPABOEntity[BOEntityType.values().length];
        entities[BOEntityType.Default.ordinal()] = new JPABOEntity(BOEntityType.Default);
        entities[BOEntityType.Playbook.ordinal()] = new JPABOEntity(BOEntityType.Playbook);
        entities[BOEntityType.Order.ordinal()] = new JPABOEntity(BOEntityType.Order);
        entities[BOEntityType.Transaction.ordinal()] = new JPABOEntity(BOEntityType.Transaction);
    }

}
