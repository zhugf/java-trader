package trader.service.tradlet;

import java.util.Collection;

/**
 * 交易策略管理服务: 加载, 更新和禁用
 */
public interface TradletService {

    /**
     * 返回加载的所有的策略实现类
     */
    public Collection<TradletInfo> getTradletInfos();

    /**
     * 返回指定的策略工厂实现类
     */
    public TradletInfo getTradletInfo(String tradletId);

    /**
     * 策略组列表
     */
    public Collection<TradletGroup> getGroups();

    /**
     * 返回指定策略组
     */
    public TradletGroup getGroup(String groupId);

}
