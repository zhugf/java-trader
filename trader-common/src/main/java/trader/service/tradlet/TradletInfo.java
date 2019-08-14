package trader.service.tradlet;

import trader.common.util.JsonEnabled;
import trader.service.plugin.Plugin;

/**
 * 交易策略实现类的运行时信息
 */
public interface TradletInfo extends JsonEnabled {

    /**
     * 全局唯一ID
     */
    public String getId();

    /**
     * 所在Plugin, 如果是从标准Classpath加载, 返回null. 意味着这个类不能被动态重新加载
     */
    public Plugin getPlugin();

    /**
     * 实现类
     */
    public Class getTradletClass();

    /**
     * 返回Plugin的更新时间. 0 代表从标准Classpath加载, 无法更新.
     */
    public long getTimestamp();

}
