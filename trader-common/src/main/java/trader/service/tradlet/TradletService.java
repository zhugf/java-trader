package trader.service.tradlet;

import java.util.Collection;

import com.google.gson.JsonObject;

import trader.common.beans.Lifecycle;
import trader.common.beans.ServiceStateAware;
import trader.common.exception.AppException;

/**
 * 交易策略管理服务: 加载, 更新和禁用
 */
public interface TradletService extends ServiceStateAware {

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

    /**
     * 明确重新加载策略组
     *
     * @return JSON 格式加载结果
     */
    public JsonObject reloadGroups(boolean force) throws AppException;

    /**
     * 添加内部事件回调函数
     */
    public void addListener(TradletServiceListener listener);
}
