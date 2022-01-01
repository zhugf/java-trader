package trader.service.ta;

import trader.common.beans.BeansContainer;

/**
 * 可定制的技术分析项目实现的创建工厂类
 * <p>TODO 自动加载
 */
public interface TechnicalAnalysisItemFactory<T> {

    public boolean accept(String itemName);

    public TechnicalAnalysisItem<T> create(BeansContainer beansContainer, BarAccess taAccess, String itemName);

}
