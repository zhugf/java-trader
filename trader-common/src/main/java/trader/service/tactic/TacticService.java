package trader.service.tactic;

import java.util.Collection;

/**
 * 交易策略管理服务: 加载, 更新和禁用
 */
public interface TacticService {

    /**
     * 返回加载的所有的策略实现类
     */
    public Collection<TacticMetadata> getTacticMetadatas();

    /**
     * 返回指定的策略实现类
     */
    public TacticMetadata getTacticMetadata(String tacticId);

    /**
     * 策略组列表
     */
    public Collection<TacticGroup> getGroups();

    /**
     * 返回指定策略组
     */
    public TacticGroup getGroup(String groupId);

}
