package trader.service.plugin;

import java.util.List;

@FunctionalInterface
public interface PluginListener {

    public void onPluginChanged(List<Plugin> updatedPlugins);

}
