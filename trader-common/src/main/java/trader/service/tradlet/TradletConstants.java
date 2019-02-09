package trader.service.tradlet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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

    /**
     * 开仓报单数
     */
    public static final int PBVol_Openning    = 0;
    /**
     * 开仓成交数
     */
    public static final int PBVol_Open        = 1;
    /**
     * 平仓报单数
     */
    public static final int PBVol_Closing     = 2;
    /**
     * 平仓成交数
     */
    public static final int PBVol_Close       = 3;
    /**
     * 当前持仓数
     */
    public static final int PBVol_Pos         = 4;
    public static final int PBVol_Count       = PBVol_Pos+1;

    /**
     * 请求开仓价
     */
    public static final int PBMny_Opening     = 0;
    /**
     * 实际开仓价
     */
    public static final int PBMny_Open        = 1;
    /**
     * 请求平仓价
     */
    public static final int PBMny_Closing     = 2;
    /**
     * 实际平仓价
     */
    public static final int PBMny_Close       = 3;
    public static final int PBMny_Count       = PBMny_Close+1;

    public static final int PBAction_Open       = 0;
    public static final int PBAction_Close      = 1;
    public static final int PBPolicy_Count      = PBAction_Close+1;

    public static JsonElement policy2json(String[] policyIds) {
        JsonObject json = new JsonObject();
        json.addProperty("open", policyIds[PBAction_Open]);
        json.addProperty("close", policyIds[PBAction_Close]);
        return json;
    }


    /**
     * 止损策略
     */
    public static enum StopLossPolicy{
        /**
         * 基于阶梯价格-持续时间止损策略, Runtime 为 StopLossPriceStep 对象实例
         */
        PriceStep
        /**
         * 最长持仓时间的止损策略, Runtime 为 Integer 对象
         */
        ,MaxLifeTime
        /**
         * 最后持仓时间的止损策略, Runtime 为 long (epoch mills), 合适与System.currTime()比较
         */
        ,EndTime
    }

    /**
     * 价格止损的运行时, object[]
     */
    public static final String PBATTR_STOPLOSS_RUNTIME = "stopLoss.runtime";

    /**
     * 止损价格阶梯, 格式为: Price1=Duration1;Price2=Duration2, 例如: 275.95=60s;5T=20S;8t=5S;15T=0
     */
    public static final String PBATTR_STOPLOSS_PRICE_STEPS = "stopLoss.priceSteps";
    /**
     * 最长持仓时间(duration), 例如: 5m 或 60s
     */
    public static final String PBATTR_STOPLOSS_MAX_LIFE_TIME = "stopLoss.maxLifeTime";
    /**
     * 最后持仓时间(timestamp或time), 例如: 14:55:00
     */
    public static final String PBATTR_STOPLOSS_END_TIME = "stopLoss.endTime";
}
