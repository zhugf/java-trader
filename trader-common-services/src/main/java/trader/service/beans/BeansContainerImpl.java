package trader.service.beans;
import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;

@Primary
@Service
public class BeansContainerImpl implements BeansContainer {

    @Autowired
    private ApplicationContext appContext;

    @Override
    public <T> T getBean(String clazz) {
        try {
            return (T)appContext.getBean(clazz);
        }catch(Throwable t) {}
        return null;
    }

    @Override
    public <T> T getBean(Class<T> clazz) {
        try {
            return appContext.getBean(clazz);
        }catch(Throwable t) {}
        return null;
    }

    @Override
    public <T> T getBean(Class<T> clazz, String purposeOrId) {
        try {
            return appContext.getBean(clazz, purposeOrId);
        }catch(Throwable t) {}
        return null;
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        try{
            return appContext.getBeansOfType(clazz);
        }catch(Throwable t) {}
        return Collections.EMPTY_MAP;
    }

}
