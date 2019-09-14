package trader.tool;

import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import trader.common.beans.BeansContainer;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.service.util.CmdAction;

public class VersionAction implements CmdAction {

    @Override
    public String getCommand() {
        return "version";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("version");
        writer.println("\t显示版本信息");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        dumpServiceInfos(writer, getClass().getClassLoader(), new String[] {"trader"}, true);
        return 0;
    }

    /**
     * dump all service jars found from classpath
     */
    public static void dumpServiceInfos(PrintWriter writer, ClassLoader cl, String []packages, boolean matchAll) throws Exception
    {
        List<String> currPackages = new ArrayList<>(Arrays.asList(packages));
        Enumeration<URL> resources = cl.getResources("META-INF/MANIFEST.MF");
        List<String> serviceInfos = new ArrayList<>();
        while(resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try(InputStream is = url.openStream();){
                Manifest manifest = new Manifest(is);
                Attributes attrs = manifest.getMainAttributes();
                String Title = attrs.getValue("Implementation-Title");
                if ( StringUtil.isEmpty(Title) ) {
                    continue;
                }
                String matchedPackage = null;
                for(String p:currPackages) {
                    if ( Title.startsWith(p)) {
                        matchedPackage=p;
                        break;
                    }
                }
                if (matchedPackage==null ) {
                    continue;
                }
                if ( !matchAll ) {
                    currPackages.remove(matchedPackage);
                }
                String version = attrs.getValue("Implementation-Version");
                String revision = attrs.getValue("Git-Revision");
                String buildTime = attrs.getValue("Built-Time");
                StringBuilder message = new StringBuilder();
                message.append(Title).append("\t").append(version);
                if ( !StringUtil.isEmpty(revision)){
                    message.append("\t"+revision);
                }
                if ( !StringUtil.isEmpty(buildTime)) {
                    message.append("\t"+buildTime);
                }
                serviceInfos.add(message.toString());
            }
        }
        Collections.sort(serviceInfos);
        for(String l:serviceInfos) {
            writer.println(l);
        }
    }

}
