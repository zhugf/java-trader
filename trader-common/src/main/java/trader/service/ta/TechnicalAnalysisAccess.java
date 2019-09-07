package trader.service.ta;

import java.util.List;

import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.JsonEnabled;
import trader.service.ta.trend.WaveBar;
import trader.service.ta.trend.WaveBar.WaveType;

@SuppressWarnings("rawtypes")
public interface TechnicalAnalysisAccess extends JsonEnabled {

    public static enum Option{
        LineWidth
        ,StrokeThreshold;
    }

    /**
     * 关联的品种
     */
    public Exchangeable getInstrument();

    /**
     * 获得某个品种的KBar数据, 如果没有返回null.
     * <BR>要求品种必须是关注行情的品种; 不支持运行到一半时动态增加品种的kBar
     * <BR>对于MIN1以外的KBar的历史数据, 会动态从MIN1合成
     */
    public LeveledTimeSeries getSeries(PriceLevel level);

    /**
     * 返回维护的WaveBar列表
     */
    public List<WaveBar> getWaveBars(PriceLevel level, WaveType waveType);

    /**
     * 返回支持的Bar 级别列表
     */
    public List<PriceLevel> getLevels();

    /**
     * 当日的 VOLDAILY 级别, 这个函数只有在第一个TICK返回后才有数据
     */
    public PriceLevel getVoldailyLevel();

    /**
     * 返回每个品种的一些特别配置
     */
    public long getOption(Option option);

}
