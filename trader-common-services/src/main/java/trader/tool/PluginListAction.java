package trader.tool;

import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import trader.common.beans.BeansContainer;
import trader.common.util.StringUtil.KVPair;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginService;
import trader.service.util.CmdAction;

public class PluginListAction implements CmdAction {

    @Override
    public String getCommand() {
        return "plugin.list";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("plugin list");
        writer.println("\t列出已加载的插件内容");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        PluginService pluginService = beansContainer.getBean(PluginService.class);
        List<Plugin> plugins = new ArrayList<>(pluginService.getAllPlugins());
        Collections.sort(plugins, (Plugin o1, Plugin o2)->{ return o1.getId().compareTo(o2.getId()); });
        boolean needsEoln=false;
        for(Plugin plugin:plugins) {
            if ( needsEoln) {
                writer.println();
            }
            String pluginDir = plugin.getPluginDirectory().getAbsolutePath();
            List<String> classpaths = new ArrayList<>();
            if ( plugin.getClassLoaderURLs()!=null) {
                for(URL url:plugin.getClassLoaderURLs()) {
                    String file = url.getFile();
                    if ( file.startsWith(pluginDir)) {
                        file = file.substring(pluginDir.length()+1, file.length());
                    }
                    classpaths.add( file );
                }
            }
            writer.println("Plugin Id: "+plugin.getId());
            writer.println("\tPath: "+pluginDir);
            writer.println("\tProperties:");
            Properties pluginProps = plugin.getProperties();
            Set propKeys = new TreeSet(pluginProps.keySet());
            for(Object key:propKeys) {
                writer.println("\t\t"+key+" = "+pluginProps.getProperty(key.toString()));
            }
            Set<String> interfaceNames = new TreeSet<>(plugin.getExposedInterfaces());
            if (interfaceNames.size()>0) {
                writer.println("\tExport interfaces:");
                for(String in:interfaceNames) {
                    writer.println("\t\t"+in+":");
                    Map<String, Class> pluginClasses = plugin.getBeanClasses(in);
                    for(String purpose:pluginClasses.keySet()) {
                        writer.println("\t\t\t"+purpose+" = "+pluginClasses.get(purpose));
                    }
                }
            }
            if ( classpaths.size()>0) {
                writer.println("\tClasspaths:");
                for(String cp:classpaths) {
                    writer.println("\t\t"+cp);
                }
            }
            needsEoln=true;
        }
        return 0;
    }

}
