package trader.service.repository.jpa;

import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.google.gson.JsonElement;

import trader.common.beans.BeansContainer;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.common.util.concurrent.DelegateExecutor;
import trader.service.repository.AbsBOEntity;
import trader.service.repository.AbsBORepository;
import trader.service.repository.BOEntity;
import trader.service.repository.BOEntityIterator;

/**
 * SpringJPA Repository
 */
@Service
public class JPABORepository extends AbsBORepository {
    private static final Logger logger = LoggerFactory.getLogger(JPABORepository.class);

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private PlatformTransactionManager tm;

    @Autowired
    private EntityManager em;

    private ThreadLocal<TransactionStatus> currTxnStatus = new ThreadLocal<>();

    private JPABOEntity[] entities = null;

    @PostConstruct
    public void init() {
        super.init(beansContainer);
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

    @Transactional(readOnly = true)
    @Override
    public String load(BOEntityType entityType, String entityId) {
        Class jpaEntityClass = entities[entityType.ordinal()].getJPAEntityClass();
        AbsJPAEntity jpaEntity = (AbsJPAEntity)em.find(jpaEntityClass, entityId);
        String result = null;
        if ( jpaEntity!=null ) {
            result = jpaEntity.getAttrs();
        }
        return result;
    }

    @Transactional(readOnly = true)
    @Override
    public BOEntityIterator search(BOEntityType entityType, String expr) {
        Class jpaEntityClass = entities[entityType.ordinal()].getJPAEntityClass();
        Query jpaQuery = null;
        if (null!=expr && expr.toUpperCase().trim().startsWith("SELECT ")) {
            //SELECT * 构建 native query
            jpaQuery = em.createNativeQuery(expr, jpaEntityClass);
        } else {
            StringBuilder query = new StringBuilder(128);
            query.append("SELECT a FROM ").append(jpaEntityClass.getSimpleName()).append(" a ");
            if ( !StringUtil.isEmpty(expr)){
                query.append("WHERE ").append(expr);
            }
            jpaQuery = em.createQuery(query.toString(), jpaEntityClass);
        }
        List<? extends AbsJPAEntity> result = jpaQuery.getResultList();
        return new JPAEntityIterator(this, (AbsBOEntity)getBOEntity(entityType), result.iterator());
    }

    public void asyncUpdate(Runnable cmd) {
        asyncExecutor.execute(()->{
            beginTransaction(false);
            try{
                cmd.run();
                endTransaction(true);
            }catch(Throwable t) {
                logger.error("asyncUpdate failed", t);
                endTransaction(false);
            }
        });
    }

    @Override
    public void asynSave(BOEntityType entityType, String id, Object value) {
        asyncExecutor.execute(()->{
            beginTransaction(false);
            try{
                save(entityType, id, JsonUtil.object2json(value));
                endTransaction(true);
            }catch(Throwable t) {
                logger.error("asyncSave failed", t);
                endTransaction(false);
            }
        });
    }

    @Transactional
    @Override
    public void save(BOEntityType entityType, String id, JsonElement value) {
        AbsJPAEntity jpaInstance = entities[entityType.ordinal()].createJPAEntityInstance();
        jpaInstance.setId(id);
        jpaInstance.setAttrs(value);
        jpaInstance.beforeSave();
        try{
            jpaInstance = em.merge(jpaInstance);
        }catch(Exception e) {
            em.persist(jpaInstance);
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
