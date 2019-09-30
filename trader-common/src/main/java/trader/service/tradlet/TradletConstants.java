package trader.service.tradlet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.PriceUtil;
import trader.service.ServiceAttribute;
import trader.service.ServiceAttribute.AttrType;

public interface TradletConstants {
    /**
     * 状态顺序越低, 能做的事情越少
     */
    public static enum TradletGroupState {
        /**
         * 完全停止行情和交易的数据处理
         */
        Disabled,
        /**
         * 已暂停, 只可以接收行情数据, 更新内部状态, 不允许开平仓
         */
        Suspended,
        /**
         * 只允许平仓
         */
        CloseOnly,
        /**
         * 正常工作, 可开平仓
         */
        Enabled
    };

    public static enum PlaybookState{
        /**
         * 开仓过程中, 后续状态 Opened, Canceling
         */
        Opening(false)
        /**
         * 开仓完成已持仓, 后续状态 Closing
         */
        ,Opened(false)
        /**
         * 清仓过程中, 后续状态 Closed, ForceClosing, Failed
         */
        ,Closing(false)
        /**
         * 清仓失败, 紧急清仓中, 后续状态 Closed, Failed
         */
        ,ForceClosing(false)
        /**
         * 已清仓(结束)
         */
        ,Closed(true)
        /**
         * 开仓失败取消, 后续状态Canceled, 原因有:
         * <BR>开仓失败
         * <BR>开仓超时
         */
        ,Canceling(false)
        /**
         * 已取消
         */
        ,Canceled(true)
        /**
         * 失败状态, 需要手工清理
         */
        ,Failed(true);

        private boolean done;

        PlaybookState(boolean done){
            this.done = done;
        }

        /**
         * 状态已结束
         */
        public boolean isDone() {
            return done;
        }

    };

    public static enum PBVol{
    /**
     * 开仓报单数
     */
    Opening
    /**
     * 开仓成交数
     */
    ,Open
    /**
     * 平仓报单数
     */
    ,Closing
    /**
     * 平仓成交数
     */
    ,Close
    /**
     * 当前持仓数
     */
    ,Pos
    };

    public static enum PBMoney{
    /**
     * 请求开仓价
     */
    Opening
    /**
     * 实际开仓价
     */
    ,Open
    /**
     * 请求平仓价
     */
    ,Closing
    /**
     * 实际平仓价
     */
    ,Close
    }

    public static enum PBAction{
    Open
    ,Close}

    public static JsonObject pbVolume2json(int[] volumes) {
        JsonObject volumeJson = new JsonObject();
        volumeJson.addProperty("Opening", volumes[PBVol.Opening.ordinal()]);
        volumeJson.addProperty("Open", volumes[PBVol.Open.ordinal()]);
        volumeJson.addProperty("Closing", volumes[PBVol.Closing.ordinal()]);
        volumeJson.addProperty("Close", volumes[PBVol.Close.ordinal()]);
        volumeJson.addProperty("Pos", volumes[PBVol.Pos.ordinal()]);
        return volumeJson;
    }

    public static JsonObject pbMoney2json(long[] money) {
        JsonObject moneyJson = new JsonObject();
        moneyJson.addProperty("Opening", PriceUtil.long2str(money[PBMoney.Opening.ordinal()]));
        moneyJson.addProperty("Open", PriceUtil.long2str(money[PBMoney.Open.ordinal()]));
        moneyJson.addProperty("Closing", PriceUtil.long2str(money[PBMoney.Closing.ordinal()]));
        moneyJson.addProperty("Close", PriceUtil.long2str(money[PBMoney.Close.ordinal()]));
        return moneyJson;
    }

    public static JsonElement pbAction2json(String[] policyIds) {
        JsonObject json = new JsonObject();
        json.addProperty("open", policyIds[PBAction.Open.ordinal()]);
        json.addProperty("close", policyIds[PBAction.Close.ordinal()]);
        return json;
    }

    /**
     * 止损策略
     */
    public static enum StopPolicy{
        /**
         * 简单价格上触停止
         * <BR>Settings 参数为 long型Price, Runtime 为 SimpleStopPolicy
         */
        SimplePriceAbove
        /**
         * 简单价格下触停止
         */
        ,SimplePriceBelow
        /**
         * 最长持仓时间的停止策略.
         * <BR>Settings参数为最长时间 1m2s , Runtime 为 MaxLifeTimePolicy
         */
        ,MaxLifeTime
        /**
         * 最后持仓时间的停止策略.
         * <BR>Settings参数为日期yyyy-mm-dd hh:mm:ss或时间hh:mm:ss, Runtime 为 EndTimePolicy, 合适与System.currTime()比较
         */
        ,EndTime
        /**
         * 三重价格上触
         */
        ,TripPriceAbove
        /**
         * 三重价格下触
         */
        ,TripPriceBelow
        /**
         * 下降趋势中, 价格越过某个界限后, 再向上N值即触发
         */
        ,BarrieredPriceUp
        /**
         * 上升趋势中, 价格越过某个界限后, 再向下N值即触发
         */
        ,BarrieredPriceDown
        /**
         * 阶梯价格止盈
         * <BR>Settings 参数为 JsonArray of PriceStep, Runtime 为 PriceStepGainPolicy
         * @deprecated
         */
        ,PriceStepGain
        /**
         * 基于价格趋势(笔划-线段)的价格止盈策略
         * @deprecated
         */
        ,PriceTrendGain
        /**
         * 基于价格趋势(笔划-线段)的价格止损策略, Runtime为 PriceTrendPolicy
         * @deprecated
         */
        ,PriceTrendLoss
    }

    /**
     * 价格止损的运行时, object[]
     */
    public static final String PBATTR_STOP_RUNTIME = "stop.runtime";

    public static final String PBACTION_TIMEOUT = "pbTimeout";
    public static final String PBACTION_ENDTIME = "pbEndTime";
    public static final String PBACTION_MAXLIFETIME = "pbMaxLifeTime";
    public static final String PBACTION_SIMPLE_PRICE_ABOVE = "pbSimplePriceAbove";
    public static final String PBACTION_SIMPLE_PRICE_BELOW = "pbSimplePriceBelow";
    public static final String PBACTION_TRIP_PRICE_ABOVE = "pbTripPriceAbove";
    public static final String PBACTION_TRIP_PRICE_BELOW = "pbTripPriceBelow";
    public static final String PBACTION_BARRIERED_PRICE_UP = "pbBarrieredPriceUp";
    public static final String PBACTION_BARRIERED_PRICE_DOWN = "pbBarrieredPriceDown";

    /**
     * 开仓超时(毫秒), 超时后会主动撤销, 修改状态为Canceling.
     * <BR>0表示不自动超时
     */
    public static final ServiceAttribute PBATTR_OPEN_TIMEOUT = new ServiceAttribute("openTimeout", AttrType.Second, "5s");

    /**
     * 平仓超时(毫秒), 超时后会自动修改为现价成交, 修改状态为ForceClosing
     * <BR>0表示不自动强制平仓
     */
    public static final ServiceAttribute PBATTR_CLOSE_TIMEOUT = new ServiceAttribute("closeTimeout", AttrType.Second, "5s");
    /**
     * 最长生存周期(毫秒), 从开仓报单成交开始计算
     */
    public static final ServiceAttribute PBATTR_MAX_LIFETIME = new ServiceAttribute("maxLifeTime", AttrType.Second, "0s");
    /**
     * 最后生存时间, 到这个时间就必须平仓
     */
    public static final ServiceAttribute PBATTR_END_TIME = new ServiceAttribute("maxLifeTime", AttrType.DateTime, null);
    /**
     * 简单上触(价)
     */
    public static final ServiceAttribute PBATTR_SIMPLE_PRICE_ABOVE = new ServiceAttribute("simplePriceAbove", AttrType.Price, null);
    /**
     * 简单下触(价)
     */
    public static final ServiceAttribute PBATTR_SIMPLE_PRICE_BELOW = new ServiceAttribute("simplePriceBelow", AttrType.Price, null);
    /**
     * 三重上触(价)
     */
    public static final ServiceAttribute PBATTR_TRIP_PRICE_ABOVE = new ServiceAttribute("tripPriceAbove", AttrType.String, null);
    /**
     * 三重上触(价)
     */
    public static final ServiceAttribute PBATTR_TRIP_PRICE_BELOW = new ServiceAttribute("tripPriceBelow", AttrType.String, null);
    /**
     * 经过一个价格界限后, 如果价格回升超过N点即停止
     */
    public static final ServiceAttribute PBATTR_BARRIERED_PRICE_UP = new ServiceAttribute("barrieredPriceUp", AttrType.String, null);
    /**
     * 经过一个价格界限后, 如果价格下降超过N点即停止
     */
    public static final ServiceAttribute PBATTR_BARRIERED_PRICE_DOWN = new ServiceAttribute("barrieredPriceDown", AttrType.String, null);
    /**
     * TODO 经过一个价格界限后, 如果价格回升创建一个新的SECTION,即停止
     */
    public static final ServiceAttribute PBATTR_BARRIERED_SECTION_UP = new ServiceAttribute("barrieredSectionUp", AttrType.String, null);
    /**
     * TODO 经过一个价格界限后, 如果价格下降创建一个新的SECTION,即停止
     */
    public static final ServiceAttribute PBATTR_BARRIERED_SECTION_DOWN = new ServiceAttribute("barrieredSectionDown", AttrType.String, null);
    /**
     * 算法策略唯一Id
     */
    public static final ServiceAttribute PBATTR_ALGO_ID = new ServiceAttribute("algoId", AttrType.String, null);
    /**
     * Tradlet Id
     */
    public static final ServiceAttribute PBATTR_TRADLET_ID = new ServiceAttribute("tradletId", AttrType.String, null);

    /**
     * 缺省观察时间(120S)
     */
    public static final int DEFAULT_PRICE_STEP_TIME = 120*1000;

    /**
     * 缺省价格宽容度(0.1%)
     */
    public static final double DEFAULT_PRICE_TOLERANCE_PERCENT = 0.001d;

    //以下为配置参数

    public static final String ITEM_SUFFIX_DISRUPTOR_WAIT_STRATEGY = "/disruptor/waitStrategy";
    public static final String ITEM_SUFFIX_DISRUPTOR_RINGBUFFER_SIZE = "/disruptor/ringBufferSize";
    public static final String ITEM_GLOBAL_DISRUPTOR_WAIT_STRATEGY = "/TradletService"+ITEM_SUFFIX_DISRUPTOR_WAIT_STRATEGY;
    public static final String ITEM_GLOBAL_DISRUPTOR_RINGBUFFER_SIZE = "/TradletService"+ITEM_SUFFIX_DISRUPTOR_RINGBUFFER_SIZE;
    public static final String ITEM_SCRIPT_FUNCTIONS = "/TradletService/scriptFunctions";
    public static final String ITEM_TRADLETS = "/TradletService/tradlets";
    public static final String ITEM_TRADLETGROUP = "/TradletService/tradletGroup";
    public static final String ITEM_TRADLETGROUPS = ITEM_TRADLETGROUP+"[]";

}
