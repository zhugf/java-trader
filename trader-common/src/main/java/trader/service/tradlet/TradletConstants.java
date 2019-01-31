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

    public static final int PBMny_Opening     = 0;
    public static final int PBMny_Open        = 1;
    public static final int PBMny_Closing     = 2;
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

}
