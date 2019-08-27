package trader.service.tradlet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.trade.Account;
import trader.service.trade.MarketTimeService;
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
import trader.service.trade.TradeConstants.PosVolume;
import trader.service.trade.Transaction;

/**
 * 交易剧本实现类
 */
public class PlaybookImpl implements Playbook, JsonEnabled {
    private static final Logger logger = LoggerFactory.getLogger(PlaybookImpl.class);

    private TradletGroupImpl group;
    private Exchangeable instrument;
    private String id;
    private int volumes[];
    private long money[];
    private Map<String, Object> attrs = new HashMap<>();
    private PosDirection direction = PosDirection.Net;
    private List<Order> orders = new ArrayList<>();
    /**
     * 当前活动报单
     */
    private Order pendingOrder;

    private List<PlaybookStateTuple> stateTuples = new ArrayList<>();
    private PlaybookStateTuple stateTuple;
    private long openTimeout = ConversionUtil.str2seconds(DEFAULT_OPEN_TIMEOUT);
    private long closeTimeout = ConversionUtil.str2seconds(DEFAULT_CLOSE_TIMEOUT);

    public PlaybookImpl(TradletGroupImpl group, String id, PlaybookBuilder builder, PlaybookStateTuple openState) {
        this.group = group;
        this.id = id;
        this.stateTuple = openState;
        if ( openState.getOrder()!=null ) {
            orders.add(openState.getOrder());
            pendingOrder = openState.getOrder();
            instrument = openState.getOrder().getInstrument();
        }
        stateTuples.add(openState);
        direction = builder.getOpenDirection();
        volumes = new int[PBVol.values().length];
        money = new long[PBMny.values().length];
        setVolume(PBVol.Opening, builder.getVolume());
        setMoney(PBMny.Opening, builder.getOpenPrice());
        //解析参数
        Map<String, Object> attrs = builder.getAttrs();
        for(String key:attrs.keySet()) {
            setAttr(key, attrs.get(key));
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Exchangeable getInstrument() {
        return instrument;
    }

    @Override
    public List<PlaybookStateTuple> getStateTuples() {
        return stateTuples ;
    }

    @Override
    public PlaybookStateTuple getStateTuple() {
        return stateTuple;
    }

    public PlaybookStateTuple getStateTuple(PlaybookState state) {
        PlaybookStateTuple result = null;
        if ( state==null ) {
            result = stateTuples.get(0);
        }else {
            for(int i=0;i<stateTuples.size();i++) {
                PlaybookStateTuple stateTuple = stateTuples.get(i);
                if ( stateTuple.getState()==state ) {
                    result = stateTuple;
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public Object getAttr(String attr) {
        return attrs.get(attr);
    }

    @Override
    public void setAttr(String attr, Object value) {
        if ( StringUtil.isEmpty(attr) || value==null ) {
            return;
        }
        switch(attr) {
        case PBATR_OPEN_TIMEOUT:
            openTimeout = ConversionUtil.str2seconds(value.toString());
            break;
        case PBATR_CLOSE_TIMEOUT:
            closeTimeout = ConversionUtil.str2seconds(value.toString());
            break;
        }
        attrs.put(attr, value);
    }

    @Override
    public int getVolume(PBVol vol) {
        return volumes[vol.ordinal()];
    }

    public void setVolume(PBVol vol, int value) {
        volumes[vol.ordinal()] = value;
    }

    public int addVolume(PBVol vol, int toAdd) {
        int idx = vol.ordinal();
        volumes[idx]+=toAdd;
        return volumes[idx];
    }

    @Override
    public long getMoney(PBMny mnyIndex) {
        return money[mnyIndex.ordinal()];
    }

    public void setMoney(PBMny mny, long value) {
        money[mny.ordinal()] = value;
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
            addVolume(PBVol.Open, txn.getVolume());
            addVolume(PBVol.Pos, txn.getVolume());
            setMoney(PBMny.Open, odrTxnPrice);
        }else {
            addVolume(PBVol.Close, txn.getVolume());
            addVolume(PBVol.Pos, txn.getVolume());
            setMoney(PBMny.Close, odrTxnPrice);
        }

        if ( getVolume(PBVol.Pos)==0 ) {
            direction = PosDirection.Net;
        }
    }

    /**
     * 当Order发生变化时, 同步更新状态
     *
     * @return 如果发送变化, 返回旧的状态; 如果没有状态变化返回null
     */
    public PlaybookStateTuple updateStateOnOrder(Order order) {
        OrderState odrState = order.getStateTuple().getState();
        OrderSubmitState odrSubmitState = order.getStateTuple().getSubmitState();
        PlaybookState newState = null;
        PlaybookState state = stateTuple.getState();
        Order newStateOrder = null;
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
        PlaybookStateTuple result = null;
        if ( newState!=null ) {
            result = changeStateTuple(newState, newStateOrder, null);
        }
        return result;
    }

    /**
     * 定期检查是否需要取消当前报单或强制平仓
     *
     * @return 如果发送变化, 返回旧的状态; 如果没有状态变化返回null
     */
    public PlaybookStateTuple updateStateOnNoop() {
        PlaybookState newState = null;
        Order newStateOrder = null;
        long currTime = System.currentTimeMillis();
        long stateTime = stateTuple.getTimestamp();
        switch (stateTuple.getState()) {
        case Opening: {// 检查是否要超时
            if (openTimeout > 0 && (currTime - stateTime) >= openTimeout * 1000) {
                newState = PlaybookState.Canceling;
                newStateOrder = stateTuple.getOrder();
            }
            break;
        }
        case Closing: { // 检查是否平仓超时
            if (closeTimeout > 0 && (currTime - stateTime) >= closeTimeout * 1000) {
                newState = PlaybookState.ForceClosing;
                newStateOrder = stateTuple.getOrder();
            }
            break;
        }
        default:
            break;
        }
        PlaybookStateTuple result = null;
        if ( newState!=null ) {
            result = changeStateTuple(newState, newStateOrder, PBACTION_TIMEOUT);
        }
        return result;
    }

    /**
     * 取消开仓中的报单
     */
    public boolean cancelOpeningOrder() {
        boolean result = false;
        if ( stateTuple.getState()==PlaybookState.Opening ) {
            result = changeStateTuple(PlaybookState.Canceling, stateTuple.getOrder(), null)!=null;
        }
        return result;
    }

    /**
     * 平仓已开仓持有仓位
     */
    public boolean closeOpenedOrder(String actionId) {
        boolean result = false;
        if ( stateTuple.getState()==PlaybookState.Opened ) {
            result = changeStateTuple(PlaybookState.Closing, null, actionId)!=null;
        }
        return result;
    }

    /**
     * 切换到新的StateTuple, 这个过程可能会对当前报单有撤销或修改, 或创建新的报单
     *
     * @return 返回旧状态, 如果没有更新, 返回null
     */
    private PlaybookStateTuple changeStateTuple(PlaybookState newState, Order newStateOrder, String actionId)
    {
        PlaybookStateTuple oldStateTuple = stateTuple;
        BeansContainer beansContainer = group.getBeansContainer();
        Account account = group.getAccount();
        OrderAction orderAction = null;
        Order stateOrder = newStateOrder;
        if ( newState!=oldStateTuple.getState() ) {
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
                if ( getVolume(PBVol.Pos)==0 ) {
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
                    addVolume(PBVol.Closing, odrBuilder.getVolume());
                    setMoney(PBMny.Closing, odrBuilder.getLimitPrice());
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
                        if ( !StringUtil.isEmpty(actionId)) {
                            odrBuilder.setAttr(Order.ODRATTR_PLAYBOOK_ACTION_ID, actionId);
                        }
                        stateOrder = account.createOrder(odrBuilder);
                        orders.add(stateOrder);
                        pendingOrder = stateOrder;
                        addVolume(PBVol.Closing, odrBuilder.getVolume());
                        setMoney(PBMny.Closing, odrBuilder.getLimitPrice());
                    }
                }catch(AppException e) {
                    //强制平仓失败, 手工处理
                    newState = PlaybookState.Failed;
                    logger.error("Playbook "+getId()+" force close "+orderRef+" failed: "+e.getMessage(), e);
                }
            }
                break;
            }
        }
        PlaybookStateTuple result = null;
        if ( newState!=oldStateTuple.getState()) {
            MarketTimeService mtService = group.getBeansContainer().getBean(MarketTimeService.class);
            PlaybookStateTupleImpl newStateTuple = new PlaybookStateTupleImpl(mtService, newState, stateOrder, orderAction, actionId);
            this.stateTuples.add(newStateTuple);
            this.stateTuple = newStateTuple;
            result = oldStateTuple;
        }
        return result;
    }

    /**
     * 创建平仓报单
     * <BR>暂时不考虑滑点
     */
    private OrderBuilder createCloseOrderBuilder(BeansContainer beansContainer, Account account, OrderPriceType priceType) {
        MarketDataService mdService = beansContainer.getBean(MarketDataService.class);
        MarketData md = mdService.getLastData(instrument);
        long closePrice = 0;
        if ( direction==PosDirection.Long ) {
            //平多卖出, 使用买1价
            closePrice = md.lastBidPrice();
        }else {
            //平空买回, 使用卖1价
            closePrice = md.lastAskPrice();
        }

        PosVolume posVolYdType, posVolTodayType;
        OrderDirection odrDirection;
        OrderOffsetFlag odrOffsetFlag = OrderOffsetFlag.CLOSE;
        if ( direction==PosDirection.Long ) {
            posVolYdType = TradeConstants.PosVolume.LongYdPosition;
            posVolTodayType = TradeConstants.PosVolume.LongTodayPosition;
            odrDirection = OrderDirection.Sell;
        }else {
            posVolYdType = TradeConstants.PosVolume.ShortYdPosition;
            posVolTodayType = TradeConstants.PosVolume.ShortTodayPosition;
            odrDirection = OrderDirection.Buy;
        }
        Position pos = account.getPosition(instrument);
        if ( pos!=null && instrument.exchange()==Exchange.SHFE ) { //上期考虑平今平昨
            if ( pos.getVolume(posVolYdType)>=getVolume(PBVol.Pos) ) {
                //昨仓足够, 使用平昨
                odrOffsetFlag = OrderOffsetFlag.CLOSE_YESTERDAY;
            } else if (pos.getVolume(posVolTodayType)>=getVolume(PBVol.Pos) ) {
                //今仓足够, 使用平今
                odrOffsetFlag = OrderOffsetFlag.CLOSE_TODAY;
            }
        }
        OrderBuilder odrBuilder = new OrderBuilder()
            .setExchagneable(instrument)
            .setVolume(getVolume(PBVol.Pos))
            .setAttr(Order.ODRATTR_PLAYBOOK_ID, id)
            .setLimitPrice(closePrice)
            .setPriceType(priceType)
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
            MarketData md = mdService.getLastData(instrument);
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
                    .setExchagneable(instrument)
                    .setLimitPrice(closePrice);

            account.modifyOrder(order.getRef(), odrBuilder);
        }
        return result;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("groupId", group.getId());
        json.addProperty("instrument", instrument.toString());
        json.add("stateTuple", JsonUtil.object2json(stateTuple));
        json.add("stateTuples", JsonUtil.object2json(stateTuples));
        json.addProperty("direction", direction.name());
        json.add("volumes",  TradletConstants.pbVolume2json(volumes));
        json.add("money",  TradletConstants.pbMoney2json(money));
        if( attrs!=null ) {
            json.add("attrs", JsonUtil.object2json(attrs));
        }
        JsonArray ordersJson = new JsonArray();
        for(Order order:orders) {
            ordersJson.add(order.getRef());
        }
        json.add("orders", ordersJson);
        if ( pendingOrder!=null ) {
            json.addProperty("pendingOrder", pendingOrder.getRef());
        }
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
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
