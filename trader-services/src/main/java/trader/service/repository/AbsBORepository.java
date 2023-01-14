package trader.service.repository;

import java.util.concurrent.ExecutorService;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceState;
import trader.common.util.StringUtil;
import trader.common.util.concurrent.DelegateExecutor;

public abstract class AbsBORepository implements BORepository {

    protected ExecutorService executorService;
    protected DelegateExecutor asyncExecutor;
    protected volatile ServiceState state = ServiceState.Stopped;

    @Override
    public Object loadEntity(BOEntityType entityType, String entityId) {
        Object result = null;
        String data = load(entityType, entityId);
        if ( !StringUtil.isEmpty(data)) {
            result = ((AbsBOEntity)getBOEntity(entityType)).data2entity(this, entityId, data);
        }
        return result;
    }

    protected void init(BeansContainer beansContainer) {
        executorService = beansContainer.getBean(ExecutorService.class);
        asyncExecutor = new DelegateExecutor(executorService, 1);
    }

}
