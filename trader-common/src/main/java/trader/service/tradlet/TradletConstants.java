package trader.service.tradlet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.PriceUtil;

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

    public static enum PBMny{
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
        moneyJson.addProperty("Opening", PriceUtil.long2str(money[PBMny.Opening.ordinal()]));
        moneyJson.addProperty("Open", PriceUtil.long2str(money[PBMny.Open.ordinal()]));
        moneyJson.addProperty("Closing", PriceUtil.long2str(money[PBMny.Closing.ordinal()]));
        moneyJson.addProperty("Close", PriceUtil.long2str(money[PBMny.Close.ordinal()]));
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
         * 简单价格触碰止损.
         * <BR>Settings 参数为 long型Price, Runtime 为 SimpleStopPolicy
         */
        SimpleLoss
        /**
         * 基于价格趋势(笔划-线段)的价格止损策略, Runtime为 PriceTrendPolicy
         * @deprecated
         */
        ,PriceTrendLoss
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
         * 阶梯价格止盈
         * <BR>Settings 参数为 JsonArray of PriceStep, Runtime 为 PriceStepGainPolicy
         */
        ,PriceStepGain
        /**
         * 基于价格趋势(笔划-线段)的价格止盈策略
         * @deprecated
         */
        ,PriceTrendGain
    }

    /**
     * 价格止损的运行时, object[]
     */
    public static final String PBATTR_STOP_RUNTIME = "stop.runtime";

    /**
     * 价格止损的配置参数, 类型为 GSON JsonObject 或 JSON String
     * <BR>在创建Playbook时, 必须设置这个Attr, 才能让StopTradlet自动执行止盈止损
     */
    public static final String PBATTR_STOP_SETTINGS = "stop.settings";

    /**
     * 缺省观察时间(120S)
     */
    public static final int DEFAULT_PRICE_STEP_TIME = 120*1000;

    /**
     * 缺省价格宽容度(0.1%)
     */
    public static final double DEFAULT_PRICE_TOLERANCE_PERCENT = 0.001d;
}
