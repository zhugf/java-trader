package trader.simulator.trade;

import trader.common.exchangeable.Exchangeable;

/**
 * 模拟CTP报单回报
 */
public class SimResponse {
    public static enum ResponseType{
        /**
         * 发单错误回报（交易所）
         */
        ErrRtnOrderInsert
        /**
         * 撤单错误回报（交易所）
         */
        ,ErrRtnOrderAction
        /**
         * 发单错误（柜台）
         */
        ,RspOrderInsert
        /**
         * 撤单错误回报（柜台）
         */
        ,RspOrderAction
        /**
         * 委托回报(报单和撤单)
         */
        ,RtnOrder
        /**
         * 成交回报
         */
        ,RtnTrade
    }

    private Exchangeable instrument;
    private ResponseType type;
    private Object[] data;

    public SimResponse(Exchangeable instrument, ResponseType type, Object ...data) {
        this.instrument = instrument;
        this.type = type;
        this.data = data;
    }

    public Exchangeable getInstrument() {
        return instrument;
    }

    public ResponseType getType() {
        return type;
    }

    public Object[] getData() {
        return data;
    }

}
