package trader.service.ta.trend;

import org.ta4j.core.num.Num;

import trader.service.ta.Bar2;

/**
 * 创建WaveBar的一些选项
 */
public class WaveBarOption {
    public static interface BarPriceGetter{

        public Num getPrice(Bar2 bar);

    }

    /**
     * 笔划反转的最大容许波动
     */
    public Num strokeThreshold;

    /**
     * 笔划价格的获取接口, 缺省是ClosPrice
     */
    public BarPriceGetter strokeBarPriceGetter = new BarPriceGetter() {

        @Override
        public Num getPrice(Bar2 bar) {
            return bar.getClosePrice();
        }
    };

}
