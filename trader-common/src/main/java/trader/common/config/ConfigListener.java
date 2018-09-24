package trader.common.config;

/**
 * Listen on config item changed
 */
public interface ConfigListener {

    public void onConfigReload(String source, String path, Object newValue);

}
