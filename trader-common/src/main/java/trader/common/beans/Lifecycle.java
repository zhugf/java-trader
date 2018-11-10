package trader.common.beans;

/**
 * 生命周期接口
 */
public interface Lifecycle {

    public void init(BeansContainer beansContainer) throws Exception;

    public void destroy();

}
