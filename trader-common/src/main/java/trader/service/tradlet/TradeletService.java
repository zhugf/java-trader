package trader.service.tradlet;

import java.util.Collection;

/**
 * 交易策略管理服务: 加载, 更新和禁用
 */
public interface TradeletService {

    /**
     * 返回加载的所有的策略实现类
     */
    public Collection<TradletMetadata> getTacticMetadatas();

    /**
     * 返回指定的策略实现类
     */
    public TradletMetadata getTacticMetadata(String tacticId);

    /**
     * 策略组列表
     */
    public Collection<TradletGroup> getGroups();

    /**
     * 返回指定策略组
     */
    public TradletGroup getGroup(String groupId);

}
