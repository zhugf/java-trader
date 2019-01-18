package trader.service.tradlet;

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
         * 初始化中, 后续状态 Openning
         */
        Unknown(false)
        /**
         * 开仓过程中, 后续状态Opened, Canceling
         */
        ,Openning(false)
        /**
         * 开仓完成已持仓, 后续状态 Closing
         */
        ,Opened(false)
        /**
         * 平仓过程中, 后续状态Closed
         */
        ,Closing(false)
        /**
         * 已平仓(结束)
         */
        ,Closed(true)
        /**
         * 取消中(开仓超时取消), 后续状态Canceled
         */
        ,Canceling(false)
        /**
         * 已取消开仓报单
         */
        ,Canceled(true)
        /**
         * 创建失败
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

}
