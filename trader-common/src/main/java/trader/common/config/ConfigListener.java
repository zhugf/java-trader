package trader.common.config;

/**
 * Listen on config item changed
 */
public interface ConfigListener {

    public void onConfigChanged(String path, Object newValue);

}
