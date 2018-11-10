package trader.service.plugin;

import java.io.File;
import java.util.List;

import trader.common.beans.BeansContainer;

/**
 * 实现PluginAware缺省回调函数的抽象类
 */
public abstract class PluginAwareAdapter implements PluginAware {

    @Override
    public void init(BeansContainer beansContainer) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void onFileUpdated(List<File> files) {
    }

}
