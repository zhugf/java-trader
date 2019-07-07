package trader.api.plugin;

import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import trader.api.ControllerConstants;
import trader.common.util.FileUtil;
import trader.common.util.JsonUtil;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginService;

@RestController
public class PluginController {

    private static final String URL_PREFIX = ControllerConstants.URL_PREFIX+"/plugin";

    @Autowired
    private PluginService pluginService;

    @RequestMapping(path=URL_PREFIX,
            method=RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
            )
    public ResponseEntity<String> reload(@RequestParam(name="pretty", required=false) boolean pretty)
    {
        return ResponseEntity.ok(JsonUtil.json2str(JsonUtil.object2json(pluginService.reload()), pretty));
    }

    @RequestMapping(path=URL_PREFIX,
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
            )
    public ResponseEntity<String> getAllPlugins(@RequestParam(name="pretty", required=false) boolean pretty)
    {
        return ResponseEntity.ok(JsonUtil.json2str(JsonUtil.object2json(pluginService.getAllPlugins()), pretty));
    }

    @RequestMapping(path=URL_PREFIX+"/{pluginId}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPlugin( @PathVariable(value="pluginId") String pluginId){
        Plugin plugin = pluginService.getPlugin(pluginId);
        if ( null==plugin ) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(plugin.toString());
    }

    @RequestMapping(path=URL_PREFIX+"/{pluginId}/{filePath:.+}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPluginFile( @PathVariable(value="pluginId") String pluginId, @PathVariable(value="filePath") String filePath) {
        Plugin plugin = pluginService.getPlugin(pluginId);
        if ( null==plugin ) {
            return ResponseEntity.notFound().build();
        }
        File pluginDir = plugin.getPluginDirectory();
        File file = new File(pluginDir, filePath);
        if ( !file.exists()) {
            return ResponseEntity.notFound().build();
        }
        if ( file.isFile() ) {
            try{
                String content = FileUtil.read(file);
                return ResponseEntity.ok(content);
            }catch(Throwable t) {}
        }
        return ResponseEntity.badRequest().build();
    }

}
