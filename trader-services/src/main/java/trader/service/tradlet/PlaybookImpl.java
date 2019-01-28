package trader.service.tradlet;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.trade.Account;
import trader.service.trade.Order;
import trader.service.trade.OrderBuilder;
import trader.service.trade.Position;
import trader.service.trade.TradeConstants;
import trader.service.trade.TradeConstants.OrderAction;
import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;
import trader.service.trade.TradeConstants.OrderPriceType;
import trader.service.trade.TradeConstants.OrderState;
import trader.service.trade.TradeConstants.OrderSubmitState;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.trade.Transaction;

/**
 * 交易剧本实现类
 */
public class PlaybookImpl implements Playbook, JsonEnabled {
    private static final Logger logger = LoggerFactory.getLogger(PlaybookImpl.class);

    private Exchangeable e;
    private String id;
    private String templateId;
    private String policyIds[];
    private int volumes[];
    private long money[];
    private Properties attrs = new Properties();
    private PosDirection direction = PosDirection.Net;
    private List<Order> orders = new ArrayList<>();
    /**
     * 当前活动报单
     */
    private Order pendingOrder;

    private Order newStateOrder;

    private List<PlaybookStateTuple> stateTuples = new ArrayList<>();
    private PlaybookStateTuple stateTuple;
    private int openTimeout = DEFAULT_OPEN_TIMEOUT;
    private int closeTimeout = DEFAULT_CLOSE_TIMEOUT;

    public PlaybookImpl(String id, PlaybookBuilder builder, PlaybookStateTuple openState) {
        this.id = id;
        this.stateTuple = openState;
        if ( openState.getOrder()!=null ) {
            orders.add(openState.getOrder());
            pendingOrder = openState.getOrder();
            e = openState.getOrder().getExchangeable();
        }
        stateTuples.add(openState);
        templateId = builder.getTemplateId();
        direction = builder.getOpenDirection();
        volumes = new int[PBVol_Count];
        money = new long[PBMny_Count];
        volumes[PBVol_Openning] = builder.getVolume();
        money[PBMny_Opening] = builder.getOpenPrice();
        //解析参数
        Properties attrs = builder.getAttrs();
        for(Object key0:attrs.keySet()) {
            String key = key0.toString();
            String val = attrs.getProperty(key);
            setAttr(key, val);
        }
        policyIds = new String[PBPolicy_Count];
        if ( !StringUtil.isEmpty(builder.getPolicyId()) ) {
            policyIds[PBPolicy_Open] = builder.getPolicyId();
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTemplateId() {
        return templateId;
    }

    @Override
    public String getPolicyId(int purposeIdx) {
        return policyIds[purposeIdx];
    }

    @Override
    public void setPolicyId(int purposeIdx, String policyId) {
        if ( policyIds[purposeIdx]!=null) {
            policyIds[purposeIdx] = policyId;
        }
    }

    @Override
    public List<PlaybookStateTuple> getStateTuples() {
        return stateTuples ;
    }

    @Override
    public PlaybookStateTuple getStateTuple() {
        return stateTuple;
    }

    @Override
    public String getAttr(String attr) {
        return attrs.getProperty(attr);
    }

    @Override
    public void setAttr(String attr, String value) {
        if ( StringUtil.isEmpty(attr) || StringUtil.isEmpty(value)) {
            return;
        }
        switch(attr) {
        case ATTR_OPEN_TIMEOUT:
            openTimeout = ConversionUtil.toInt(attrs.getProperty(ATTR_OPEN_TIMEOUT), true);
            break;
        case ATTR_CLOSE_TIMEOUT:
            closeTimeout = ConversionUtil.toInt(attrs.getProperty(ATTR_CLOSE_TIMEOUT), true);
            break;
        }
        attrs.setProperty(attr, value);
    }

    @Override
    public int getVolume(int volIndex) {
        return volumes[volIndex];
    }

    @Override
    public long getMoney(int mnyIndex) {
        return money[mnyIndex];
    }

    @Override
    public PosDirection getDirection() {
        return direction;
    }

    @Override
    public List<Order> getOrders() {
        return orders;
    }

    @Override
    public Order getPendingOrder() {
        return pendingOrder;
    }

    public void updateOnTxn(Transaction txn) {
        Order order=txn.getOrder();
        int odrTxnVolume=0;
        long odrTxnTurnover = 0;
        for(Transaction odrTxn:order.getTransactions()) {
            odrTxnVolume += odrTxn.getVolume();
            odrTxnTurnover = (odrTxn.getPrice()*odrTxn.getVolume());
        }
        long odrTxnPrice = odrTxnTurnover/odrTxnVolume;
        if ( order.getOffsetFlags()==OrderOffsetFlag.OPEN ) {
            volumes[PBVol_Open] += txn.getVolume();
            volumes[PBVol_Pos] += txn.getVolume();
            money[PBMny_Open] = odrTxnPrice;
        }else {
            volumes[PBVol_Close] += txn.getVolume();
            volumes[PBVol_Pos] -= txn.getVolume();
            money[PBMny_Close] = odrTxnPrice;
        }

        if ( volumes[PBVol_Pos]==0 ) {
            direction = PosDirection.Net;
        }
    }

    /**
     * 当Order发生变化时, 同步更新状态
     */
    public PlaybookState checkStateOnOrder(Order order) {
        OrderState odrState = order.getStateTuple().getState();
        OrderSubmitState odrSubmitState = order.getStateTuple().getSubmitState();
        PlaybookState newState = null;
        PlaybookState state = stateTuple.getState();
        newStateOrder = null;
        if ( odrState.isDone() ) {
            pendingOrder = null;
        }else {
            pendingOrder=order;
        }

        if ( state.isDone() ) {
            return null;
        }
        switch(stateTuple.getState()) {
        case Opening:
        case Canceling:
            switch(odrState) {
            case Failed: //开仓失败
                newState = PlaybookState.Failed;
                newStateOrder = order;
                break;
            case Complete: //开仓成功
                newState = PlaybookState.Opened;
                newStateOrder = order;
                break;
            case Submitted: //取消开仓
                if ( odrSubmitState==OrderSubmitState.CancelSubmitted ) {
                    newState = PlaybookState.Canceling;
                }
                newStateOrder = order;
                break;
            case Canceled: //已取消开仓
                newState = PlaybookState.Canceled;
                newStateOrder = order;
                break;
            }
            break;
        case Closing:
            switch(odrState) {
            case Failed: //正常平仓失败, 需要强制平仓
                newState = PlaybookState.ForceClosing;
                break;
            case Complete: //平仓成功
                newState = PlaybookState.Closed;
                newStateOrder = order;
                break;
            case Canceled: //平仓报单被撤销, 需要强制平仓
                newState = PlaybookState.ForceClosing;
                break;
            }
            break;
        case ForceClosing:
            { //强制清仓报单, 只需要关注部分状态
                switch(odrState) {
                case Complete: //强制平仓成功
                    newState = PlaybookState.Closed;
                    newStateOrder = order;
                    break;
                case Failed:
                case Canceled: //强制平仓报单被撤销或失败, 平仓失败
                    newState = PlaybookState.Failed;
                    newStateOrder = order;
                    break;
                }
            }
            break;
        }
        return newState;
    }

    /**
     * 定期检查是否需要取消当前报单或强制平仓
     */
    public PlaybookState checkStateOnNoop() {
        PlaybookState result = null;
        newStateOrder = null;
        long currTime = System.currentTimeMillis();
        long stateTime = stateTuple.getTimestamp();
        switch (stateTuple.getState()) {
        case Opening: {// 检查是否要超时
            if (openTimeout > 0 && (currTime - stateTime) >= openTimeout) {
                result = PlaybookState.Canceling;
                newStateOrder = stateTuple.getOrder();
            }
        }
            break;
        case Closing: { //检查是否平仓超时
            if ( closeTimeout>0 && (currTime-stateTime)>=closeTimeout) {
                result = PlaybookState.ForceClosing;
                newStateOrder = stateTuple.getOrder();
            }
        }
            break;
        default:
            break;
        }
        return result;
    }

    /**
     * 切换到新的StateTuple, 这个过程可能会对当前报单有撤销或修改, 或创建新的报单
     */
    public PlaybookStateTuple changeStateTuple(BeansContainer beansContainer, Account account, PlaybookState newState)
    {
        OrderAction orderAction = null;
        Order stateOrder = newStateOrder;
        switch(newState) {
        case Canceling:{ //取消当前报单
            try {
                orderAction = OrderAction.Cancel;
                account.cancelOrder(stateOrder.getRef());
            } catch (AppException e) {
                newState = PlaybookState.Failed;
                logger.error("Playbook "+getId()+" cancel failed: "+e.getMessage(), e);
            }
        }
        break;
        case Canceled:{ //对于已取消报单, 更新状态
            if ( volumes[PBVol_Pos]==0 ) {
                direction = PosDirection.Net;
            }
        }
        break;
        case Closing:{ //生成一个新的平仓报单
            stateOrder = null;
            orderAction = OrderAction.Send;
            OrderBuilder odrBuilder = createCloseOrderBuilder(beansContainer, account, OrderPriceType.LimitPrice);
            try{
                stateOrder = account.createOrder(odrBuilder);
                orders.add(stateOrder);
                pendingOrder = stateOrder;
                volumes[PBVol_Closing] += odrBuilder.getVolume();
                money[PBMny_Closing] = odrBuilder.getLimitPrice();
            }catch(AppException e) {
                //平仓失败, 手工处理
                newState = PlaybookState.Failed;
                logger.error("Playbook "+getId()+" close failed: "+e.getMessage(), e);
            }
        }
        break;
        case ForceClosing:{ //用市场价修改当前报单, 或再次用当前市场价生成一个新的报单
            String orderRef = "";
            try{
                if ( stateOrder!=null ) {
                    orderRef = stateOrder.getRef();
                    orderAction = modifyCloseOrder(beansContainer, account, stateOrder);
                } else {
                    orderAction = OrderAction.Send;
                    OrderBuilder odrBuilder = createCloseOrderBuilder(beansContainer, account, OrderPriceType.BestPrice);
                        stateOrder = account.createOrder(odrBuilder);
                        orders.add(stateOrder);
                        pendingOrder = stateOrder;
                        volumes[PBVol_Closing] += odrBuilder.getVolume();
                        money[PBMny_Closing] = odrBuilder.getLimitPrice();
                }
            }catch(AppException e) {
                //强制平仓失败, 手工处理
                newState = PlaybookState.Failed;
                logger.error("Playbook "+getId()+" force close "+orderRef+" failed: "+e.getMessage(), e);
            }
        }
            break;
        }
        PlaybookStateTupleImpl result = new PlaybookStateTupleImpl(newState, stateOrder, orderAction);
        this.stateTuple = result;
        this.stateTuples.add(result);
        return result;
    }

    /**
     * 创建平仓报单
     * <BR>暂时不考虑滑点
     */
    private OrderBuilder createCloseOrderBuilder(BeansContainer beansContainer, Account account, OrderPriceType priceType) {
        MarketDataService mdService = beansContainer.getBean(MarketDataService.class);
        MarketData md = mdService.getLastData(e);
        long closePrice = 0;
        if ( direction==PosDirection.Long ) {
            //平多卖出
            closePrice = md.lastBidPrice();
        }else {
            //平空买回
            closePrice = md.lastAskPrice();
        }

        int posVolType = 0;
        OrderDirection odrDirection;
        OrderOffsetFlag odrOffsetFlag = OrderOffsetFlag.CLOSE;
        if ( direction==PosDirection.Long ) {
            posVolType = TradeConstants.PosVolume_LongYdPosition;
            odrDirection = OrderDirection.Sell;
        }else {
            posVolType = TradeConstants.PosVolume_ShortYdPosition;
            odrDirection = OrderDirection.Buy;
        }
        Position pos = account.getPosition(e);
        if ( pos!=null ) {
            if ( pos.getVolume(posVolType)==0 ) {
                //无昨仓, 使用平今
                odrOffsetFlag = OrderOffsetFlag.CLOSE_TODAY;
            }else if ( pos.getVolume(posVolType)>volumes[PBVol_Pos] ) {
                //昨仓足够, 使用平昨
                odrOffsetFlag = OrderOffsetFlag.CLOSE_YESTERDAY;
            }
        }
        OrderBuilder odrBuilder = new OrderBuilder()
            .setExchagneable(e)
            .setVolume(volumes[PBVol_Pos])
            .setAttr(ATTR_PLAYBOOK_ID, id)
            .setLimitPrice(closePrice)
            .setPriceType(priceType)
            .setPriceType(OrderPriceType.BestPrice)
            .setDirection(odrDirection)
            .setOffsetFlag(odrOffsetFlag);

        return odrBuilder;
    }

    /**
     * 强制关闭报单
     */
    private OrderAction modifyCloseOrder(BeansContainer beansContainer, Account account, Order order) throws AppException
    {
        OrderAction result = null;
        if ( !order.getStateTuple().getState().isDone() ) {
            MarketDataService mdService = beansContainer.getBean(MarketDataService.class);
            MarketData md = mdService.getLastData(e);
            result = OrderAction.Modify;

            long closePrice = 0;
            if ( direction==PosDirection.Long ) {
                //平多卖出
                closePrice = md.lastBidPrice();
            } else {
                //平空买回
                closePrice = md.lastAskPrice();
            }

            OrderBuilder odrBuilder = new OrderBuilder()
                    .setExchagneable(e)
                    .setLimitPrice(closePrice);

            account.modifyOrder(order.getRef(), odrBuilder);
        }
        return result;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("stateTuple", stateTuple.toString());
        json.addProperty("direction", direction.name());
        json.add("volumes",  JsonUtil.object2json(volumes));
        if( attrs!=null ) {
            json.add("attrs", JsonUtil.object2json(attrs));
        }
        json.add("policyIds", TradletConstants.policy2json(policyIds));
        return json;
    }

    @Override
    public boolean equals(Object o) {
        if ( this==o ) {
            return true;
        }
        if ( null==o || !(o instanceof PlaybookImpl)) {
            return false;
        }
        PlaybookImpl p = (PlaybookImpl)o;

        return id.equals(p.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
