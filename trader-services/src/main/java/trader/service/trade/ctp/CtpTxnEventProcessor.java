package trader.service.trade.ctp;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import net.jctp.CThostFtdcInputOrderActionField;
import net.jctp.CThostFtdcInputOrderField;
import net.jctp.CThostFtdcOrderActionField;
import net.jctp.CThostFtdcOrderField;
import net.jctp.CThostFtdcRspInfoField;
import net.jctp.CThostFtdcRspTransferField;
import net.jctp.CThostFtdcTradeField;
import net.jctp.JctpConstants;
import trader.common.exchangeable.Exchange;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.event.AsyncEventProcessor;
import trader.service.trade.Account;
import trader.service.trade.Order;
import trader.service.trade.OrderStateTuple;
import trader.service.trade.TradeConstants;
import trader.service.trade.spi.TxnSessionListener;

/**
 * Ctp回调事件处理代码, 会在AsyncEvent的 Main Event Chain 线程中执行
 */
public class CtpTxnEventProcessor implements AsyncEventProcessor, JctpConstants, TradeConstants{

    private static ZoneId CTP_ZONE = Exchange.SHFE.getZoneId();

    public static final int DATA_TYPE_ERR_RTN_ORDER_ACTION = 0;
    public static final int DATA_TYPE_RTN_ORDER = 1;
    public static final int DATA_TYPE_RTN_TRADE = 2;
    public static final int DATA_TYPE_ERR_RTN_ORDER_INSERT = 3;
    public static final int DATA_TYPE_RSP_ORDER_INSERT = 4;
    public static final int DATA_TYPE_RSP_ORDER_ACTION = 5;
    public static final int DATA_TYPE_RTN_ACCOUNT_TRANSFER = 6;

    private CtpTxnSession session;
    private TxnSessionListener listener;
    protected Logger logger;

    public CtpTxnEventProcessor(Account account, CtpTxnSession session, TxnSessionListener listener) {
        this.session = session;
        this.listener = listener;
        logger = LoggerFactory.getLogger(account.getLoggerCategory()+"."+getClass().getSimpleName());
    }

    /**
     * 异步报单/成交回报事件处理入口函数
     */
    @Override
    public void process(int eventType, Object data, Object data2) {
        switch(eventType&0XFFFF) {
        case DATA_TYPE_RTN_ORDER:
            processRtnOrder((CThostFtdcOrderField)data);
            break;
        case DATA_TYPE_RTN_TRADE:
            processRtnTrade((CThostFtdcTradeField)data);
            break;
        case DATA_TYPE_ERR_RTN_ORDER_INSERT:
            processErrRtnOrderInsert( (CThostFtdcInputOrderField) data, (CThostFtdcRspInfoField)data2);
            break;
        case DATA_TYPE_RSP_ORDER_INSERT:
            processRspOrderInsert((CThostFtdcInputOrderField)data, (CThostFtdcRspInfoField)data2);
            break;
        case DATA_TYPE_RSP_ORDER_ACTION:
            processRspOrderAction((CThostFtdcInputOrderActionField)data,(CThostFtdcRspInfoField)data2);
            break;
        case DATA_TYPE_ERR_RTN_ORDER_ACTION:
            processErrRtnOrderAction( (CThostFtdcOrderActionField) data, (CThostFtdcRspInfoField)data2);
            break;
        case DATA_TYPE_RTN_ACCOUNT_TRANSFER:
            processRtnAccountTransferAction( (AccountTransferAction)data, (CThostFtdcRspTransferField)data2);
            break;
        default:
            logger.error("Unknown event type: "+Integer.toHexString(eventType)+", data: "+data+", data2: "+data2);
            break;
        }
    }

    /**
     * 账户转账事件处理函数
     */
    private void processRtnAccountTransferAction(AccountTransferAction transferAction, CThostFtdcRspTransferField transferInfo) {
        long tradeAmount = PriceUtil.price2long(transferInfo.TradeAmount);
        listener.onAccountTransfer(transferAction, tradeAmount);
    }

    /**
     * 报单回报(交易所)处理函数
     */
    private void processRtnOrder(CThostFtdcOrderField pOrder) {
        try{
            Map<String, String> attrs = new HashMap<>();
            if (!StringUtil.isEmpty(pOrder.OrderSysID)) {
                attrs.put(Order.ODRATR_CTP_SYS_ID, pOrder.OrderSysID);
            }
            attrs.put(Order.ODRATR_CTP_SESSION_ID, ""+pOrder.SessionID);
            attrs.put(Order.ODRATR_CTP_FRONT_ID, ""+pOrder.FrontID);
            attrs.put(Order.ODRATR_CTP_STATUS, ""+pOrder.OrderStatus);
            OrderState state = CtpUtil.ctp2orderState(pOrder.OrderStatus, pOrder.OrderSubmitStatus);
            String stateMessage = pOrder.StatusMsg;
            OrderSubmitState submitState = CtpUtil.ctp2orderSubmitState(pOrder.OrderSubmitStatus);
            switch(state){
            case Submitted:
                //submitState = (OrderSubmitState.InsertSubmitted);
                break;
            case Accepted:
                //submitState = (OrderSubmitState.Accepted);
                break;
            case Failed:
            case Canceled:
                stateMessage = (pOrder.StatusMsg);
                break;
            case Complete:
                break;
            default:
            }
            Order order = listener.getOrderByRef(pOrder.OrderRef);
            if ( order!=null ) {
                listener.onOrderStateChanged(order, new OrderStateTuple(state, submitState, System.currentTimeMillis(), stateMessage), attrs);
            } else {
                JsonObject orderInfo = field2json(pOrder);
                orderInfo.addProperty("state", state.name());
                orderInfo.addProperty("submitState", submitState.name());
                orderInfo.addProperty("stateMessage", stateMessage);
                listener.createOrderFromResponse(orderInfo);
            }
            if ( logger.isInfoEnabled() ) {
                logger.info("OnRtnOrder: "+pOrder+(order!=null?" orderId "+order.getId():""));
            }
        } catch (Throwable t) {
            logger.error("报单回报处理错误", t);
        }
    }

    /**
     * 处理成交回报
     */
    private void processRtnTrade(CThostFtdcTradeField pTrade) {
        LocalDateTime tradeTime = DateUtil.str2localdatetime(pTrade.TradeDate, pTrade.TradeTime,0);
        listener.onTransaction(
                StringUtil.trim(pTrade.TradeID),
                CtpUtil.ctp2instrument(pTrade.ExchangeID, pTrade.InstrumentID),
                pTrade.OrderRef,
                CtpUtil.ctp2orderDirection(pTrade.Direction),
                CtpUtil.ctp2orderOffsetFlag(pTrade.OffsetFlag),
                PriceUtil.price2long(pTrade.Price),
                pTrade.Volume,
                DateUtil.localdatetime2long(CTP_ZONE, tradeTime),
                pTrade
                );
        if ( logger.isInfoEnabled() ) {
            logger.info("OnRtnTrade: "+pTrade);
        }
    }

    /**
     * 报单错误(交易所)
     */
    private void processErrRtnOrderInsert(CThostFtdcInputOrderField pInputOrder, CThostFtdcRspInfoField pRspInfo)
    {
        String failReason = null;
        if ( isRspError(pRspInfo) ) {
            failReason = pRspInfo.ErrorMsg;
        } else {
            failReason = ("未知失败原因: "+pRspInfo);
        }
        Order order = listener.getOrderByRef(pInputOrder.OrderRef);
        if ( order!=null ) {
            listener.onOrderStateChanged(order, new OrderStateTuple(OrderState.Failed, OrderSubmitState.InsertRejected, System.currentTimeMillis(), failReason), null);
        }
        if ( logger.isInfoEnabled() ) {
            logger.info("OnErrRtnOrderInsert: "+pInputOrder+" "+pRspInfo+(order!=null?" orderId "+order.getId():""));
        }
    }

    /**
     * 报单错误(柜台)
     */
    private void processRspOrderInsert(CThostFtdcInputOrderField pInputOrder, CThostFtdcRspInfoField pRspInfo) {
        String failReason = null;
        if ( isRspError(pRspInfo) ) {
            failReason = pRspInfo.ErrorMsg;
        } else {
            failReason = ("未知失败原因: "+pRspInfo);
        }
        Order order = listener.getOrderByRef(pInputOrder.OrderRef);
        if ( order!=null ) {
            listener.onOrderStateChanged(order, new OrderStateTuple(OrderState.Failed, OrderSubmitState.InsertRejected, System.currentTimeMillis(), failReason), null);
        }
        if ( logger.isInfoEnabled() ) {
            logger.info("OnRspOrderInsert: "+pInputOrder+" "+pRspInfo+(order!=null?" orderId "+order.getId():"") );
        }
    }

    /**
     * 撤单错误回报（柜台）
     */
    public void processRspOrderAction(CThostFtdcInputOrderActionField pInputOrderAction, CThostFtdcRspInfoField pRspInfo) {
        String failReason = null;
        if ( isRspError(pRspInfo) ) {
            failReason = (pRspInfo.ErrorMsg);
        } else {
            failReason = ("未知失败原因: "+pRspInfo);
        }

        String orderRef = pInputOrderAction.OrderRef;
        Order order = session.getAccount().getOrderByRef(orderRef);
        OrderState odrState = OrderState.Failed;
        if ( order!=null ) {
            odrState = order.getStateTuple().getState();
        }

        OrderSubmitState submitState = OrderSubmitState.CancelRejected;
        switch(pInputOrderAction.ActionFlag) {
        case THOST_FTDC_AF_Modify:
            submitState = (OrderSubmitState.ModifyRejected);
            break;
        case THOST_FTDC_AF_Delete:
            submitState = (OrderSubmitState.CancelRejected);
            break;
        }
        listener.onOrderStateChanged(order, new OrderStateTuple(odrState, submitState, System.currentTimeMillis(), failReason), null);

        if ( logger.isInfoEnabled() ) {
            logger.info("OnRspOrderAction: "+pInputOrderAction+" "+pRspInfo);
        }
    }

    /**
     * 撤单错误回报（交易所）
     */
    public void processErrRtnOrderAction(CThostFtdcOrderActionField pOrderAction, CThostFtdcRspInfoField pRspInfo) {
        String failReason = null;
        if ( isRspError(pRspInfo) ) {
            failReason = (pRspInfo.ErrorMsg);
        } else {
            failReason = ("未知失败原因: "+pRspInfo);
        }
        String orderRef = pOrderAction.OrderRef;
        Order order = session.getAccount().getOrderByRef(orderRef);
        OrderState odrState = OrderState.Failed;
        if ( order!=null ) {
            odrState = order.getStateTuple().getState();
        }
        OrderSubmitState submitState = OrderSubmitState.CancelRejected;
        switch(pOrderAction.ActionFlag) {
        case THOST_FTDC_AF_Modify:
            submitState = (OrderSubmitState.ModifyRejected);
            break;
        case THOST_FTDC_AF_Delete:
            submitState = (OrderSubmitState.CancelRejected);
            break;
        }
        listener.onOrderStateChanged(order, new OrderStateTuple(odrState, submitState, System.currentTimeMillis(), failReason), null);

        if ( logger.isInfoEnabled() ) {
            logger.info("OnErrRtnOrderAction: "+pOrderAction+" "+pRspInfo);
        }
    }

    /**
     * 转换报单回报为Order创建用JsonObject
     */
    private static JsonObject field2json(CThostFtdcInputOrderField field) {
        JsonObject orderInfo = new JsonObject();
        orderInfo.addProperty("ref", field.OrderRef);
        orderInfo.addProperty("instrument", field.InstrumentID);
        orderInfo.addProperty("direction", CtpUtil.ctp2orderDirection(field.Direction).name());
        orderInfo.addProperty("priceType", CtpUtil.ctp2orderPriceType(field.OrderPriceType).name());
        orderInfo.addProperty("limitPrice", field.LimitPrice);
        orderInfo.addProperty("offsetFlag", CtpUtil.ctp2orderOffsetFlag(field.CombOffsetFlag.charAt(0)).name());
        return orderInfo;
    }

    private static JsonObject field2json(CThostFtdcOrderField field) {
        JsonObject orderInfo = new JsonObject();
        orderInfo.addProperty("ref", field.OrderRef);
        orderInfo.addProperty("instrument", field.InstrumentID);
        orderInfo.addProperty("direction", CtpUtil.ctp2orderDirection(field.Direction).name());
        orderInfo.addProperty("priceType", CtpUtil.ctp2orderPriceType(field.OrderPriceType).name());
        orderInfo.addProperty("limitPrice", field.LimitPrice);
        orderInfo.addProperty("offsetFlag", CtpUtil.ctp2orderOffsetFlag(field.CombOffsetFlag.charAt(0)).name());

        JsonObject attrs = new JsonObject();
        attrs.addProperty(Order.ODRATR_CTP_SESSION_ID, ""+field.SessionID);
        attrs.addProperty(Order.ODRATR_CTP_FRONT_ID, ""+field.FrontID);
        attrs.addProperty(Order.ODRATR_CTP_STATUS, ""+field.OrderStatus);
        if (!StringUtil.isEmpty(field.OrderSysID)) {
            attrs.addProperty(Order.ODRATR_CTP_SYS_ID, ""+field.OrderSysID);
        }
        orderInfo.add("attrs", attrs);
        return orderInfo;
    }

    private static boolean isRspError(CThostFtdcRspInfoField rspInfo){
        return rspInfo!=null && rspInfo.ErrorID!=0;
    }

}
