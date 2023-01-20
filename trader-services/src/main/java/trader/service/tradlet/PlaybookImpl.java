package trader.service.tradlet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLListExpr;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.exception.AppException;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.Future;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.repository.BOEntityIterator;
import trader.service.repository.BORepository;
import trader.service.repository.BORepositoryConstants.BOEntityType;
import trader.service.trade.AbsTimedEntity;
import trader.service.trade.Account;
import trader.service.trade.MarketTimeService;
import trader.service.trade.Order;
import trader.service.trade.OrderBuilder;
import trader.service.trade.OrderImpl;
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
public class PlaybookImpl extends AbsTimedEntity implements Playbook, JsonEnabled {
    private static final Logger logger = LoggerFactory.getLogger(PlaybookImpl.class);

    private String groupId;
    private TradletGroupImpl group;
    private PlaybookBuilder builder;
    private int volumes[];
    private long money[];
    private Map<String, Object> attrs = new HashMap<>();
    private volatile int version;
    private PosDirection openDirection = PosDirection.Net;
    private PosDirection direction = PosDirection.Net;
    private List<String> orderIds = new ArrayList<>();
    private List<Order> orders = new ArrayList<>();
    /**
     * 当前活动报单
     */
    private Order pendingOrder;
    private List<PlaybookStateTuple> stateTuples = new ArrayList<>();
    private volatile PlaybookStateTuple stateTuple;

    /**
     * 新建一个playbook
     */
    public PlaybookImpl(TradletGroupImpl group, String id, PlaybookBuilder builder) {
        super(id, group.getAccount().getId(), builder.getInstrument(), group.getBeansContainer().getBean(MarketTimeService.class).getTradingDay() );
        this.group = group;
        this.groupId = group.getId();
        this.builder = builder;
        MarketTimeService mtService = group.getBeansContainer().getBean(MarketTimeService.class);
        this.stateTuple = new PlaybookStateTupleImpl(mtService, PlaybookState.Init, null, null, null);
        stateTuples.add(stateTuple);
        direction = builder.getOpenDirection();
        openDirection = direction;
        volumes = new int[PBVol.values().length];
        money = new long[PBMoney.values().length];
        setVolume(PBVol.Opening, builder.getVolume());
        setMoney(PBMoney.Opening, builder.getOpenPrice());
        //解析参数
        Map<String, Object> attrs = builder.getAttrs();
        for(String key:attrs.keySet()) {
            setAttr(key, attrs.get(key));
        }
    }

    PlaybookImpl(BORepository repository, JsonObject json) {
        super(JsonUtil.getProperty(json, "id", null),
                JsonUtil.getProperty(json,"accountId",null),
                Future.fromString(json.get("instrument").getAsString()),
                JsonUtil.getPropertyAsDate(json, "tradingDay")
            );
        this.openDirection = JsonUtil.getPropertyAsEnum(json, "openDirection", PosDirection.Net, PosDirection.class);
        this.direction = JsonUtil.getPropertyAsEnum(json, "direction", PosDirection.Net, PosDirection.class);
        this.money = TradletConstants.json2PbMoney(json.get("money").getAsJsonObject());
        this.volumes = TradletConstants.json2PbVolume(json.get("volumes").getAsJsonObject());
        JsonArray stateTuplesArray = json.get("stateTuples").getAsJsonArray();
        for(int i=0;i<stateTuplesArray.size();i++) {
            PlaybookStateTupleImpl stateTuple = new PlaybookStateTupleImpl(repository, stateTuplesArray.get(i).getAsJsonObject());
            this.stateTuples.add(stateTuple);
            this.stateTuple = stateTuple;
        }
        this.groupId = json.get("groupId").getAsString();
        if (json.has("attrs")) {
            this.attrs = (Map)JsonUtil.json2value(json.get("attrs"));
        }
        this.version = JsonUtil.getPropertyAsInt(json, "version", 0);
        JsonArray orderIdsArray = json.get("orderIds").getAsJsonArray();
        for(int i=0; i<orderIdsArray.size();i++) {
            String orderId = orderIdsArray.get(i).getAsString();
            this.orderIds.add(orderId);
            OrderImpl order = OrderImpl.load(repository, orderId, null);
            if ( null!=order ) {
                this.orders.add(order);
            }
        }
        if ( json.has("pendingOrderId")) {
            String pendingOrderId = json.get("pendingOrderId").getAsString();
            pendingOrder = OrderImpl.load(repository, pendingOrderId, null);
        }
    }

    public void setGroup(TradletGroupImpl group) {
        if ( !StringUtil.equals(group.getId(), groupId)) {
            throw new RuntimeException("Playbook "+getId()+" group id "+groupId+" is not matched: "+group.getId());
        }
        this.group = group;
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
    public Object getAttr(String attr, Object defaultv) {
        Object r = attrs.get(attr);
        if (StringUtil.isEmpty(r)) {
            r = defaultv;
        }
        return r;
    }

    @Override
    public void setAttr(String attr, Object value) {
        version++;
        if ( value==null ) {
            attrs.remove(attr);
        } else {
            attrs.put(attr, value);
        }
    }

    @Override
    public int getVersion() {
        return version;
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
    public long getMoney(PBMoney mnyIndex) {
        return money[mnyIndex.ordinal()];
    }

    public void setMoney(PBMoney mny, long value) {
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

    public List<String> getOrderIds() {
        return Collections.unmodifiableList(orderIds);
    }

    @Override
    public Order getPendingOrder() {
        return pendingOrder;
    }

    public long getPositionTime() {
        MarketTimeService mtService = group.getBeansContainer().getBean(MarketTimeService.class);
        long openTime = 0;
        for(var stateTuple:stateTuples) {
            if ( stateTuple.getState()==PlaybookState.Opened ) {
                openTime = stateTuple.getTimestamp();
                break;
            }
        }
        long result = 0;
        if ( 0!=openTime ) {
            result = instrument.exchange().tradingTimeCompare(instrument, openTime, mtService.currentTimeMillis());
        }
        return result;
    }

    @Override
    public void open() throws AppException
    {
        Exchangeable instrument = builder.getInstrument();
        if ( instrument==null ) {
            instrument = group.getInstruments().get(0);
        }
        OrderPriceType priceType = builder.getPriceType();
        long openPrice = builder.getOpenPrice();
        //自动使用对手价
        if ( priceType==OrderPriceType.Unknown ) {
            MarketDataService mdService = group.getBeansContainer().getBean(MarketDataService.class);
            MarketData md = mdService.getLastData(instrument);
            if ( md!=null ) {
                if ( builder.getOpenDirection()==PosDirection.Long ) {
                    openPrice = md.lastAskPrice(); //开仓买多, 使用卖1价
                }else {
                    openPrice = md.lastBidPrice(); //开仓卖空, 使用买1价
                }
                priceType = OrderPriceType.LimitPrice;
            } else {
                priceType = OrderPriceType.BestPrice;
            }
        }
        OrderBuilder odrBuilder = new OrderBuilder();
        odrBuilder.setExchagneable(instrument)
            .setDirection(builder.getOpenDirection()==PosDirection.Long?OrderDirection.Buy:OrderDirection.Sell)
            .setLimitPrice(openPrice)
            .setPriceType(priceType)
            .setVolume(builder.getVolume())
            .setOffsetFlag(OrderOffsetFlag.OPEN)
            .setAttr(Order.ODRATTR_PLAYBOOK_ID, getId())
            .setAttr(Order.ODRATTR_TRADLET_GROUP_ID, group.getId())
            ;
        //创建报单
        try{
            Order order = group.getAccount().createOrder(odrBuilder);
            if ( logger.isInfoEnabled()) {
                logger.info("Tradlet group "+group.getId()+" playbook "+getId()+" open order "+order.getRef());
            }
            changeStateTuple(PlaybookState.Opening, order, builder.getActionId());
        }catch(AppException appe) {
            logger.error("Tradlet group "+group.getId()+" playbook "+getId()+" open order failed: "+appe.toString(), appe);
            changeStateTuple(PlaybookState.Failed, null, null);
            throw appe;
        }
    }

    public void updateOnTxn(Order order, Transaction txn) {
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
            setMoney(PBMoney.Open, odrTxnPrice);
            if ( logger.isInfoEnabled() ) {
                logger.info("交易剧本 "+getId()+" 匹配开仓成交: "+txn);
            }
        }else {
            addVolume(PBVol.Close, txn.getVolume());
            addVolume(PBVol.Pos, -1*txn.getVolume());
            setMoney(PBMoney.Close, odrTxnPrice);
            if ( logger.isInfoEnabled() ) {
                logger.info("交易剧本 "+getId()+" 匹配平仓成交: "+txn);
            }
        }
        if ( null!=pendingOrder && StringUtil.equals(order.getId(), pendingOrder.getId()) && order.getStateTuple().getState().isDone() ){
            pendingOrder = null;
        }

        if ( getVolume(PBVol.Pos)==0 ) {
            direction = PosDirection.Net;
        }
        version+=1;
        //异步保存自身状态
        group.getRepository().asynSave(BOEntityType.Playbook, getId(), this);
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
     * 当有新的行情来的时候, 检查是否需要及时关闭Playbook
     */
    public PlaybookStateTuple updateStateOnTick(MarketData tick) {
        PlaybookState newState = null;
        String newStateAction = canClose(tick);
        if ( newStateAction!=null ) {
            newState = PlaybookState.Closing;
        }
        PlaybookStateTuple result = null;
        if ( newState!=null ) {
            result = changeStateTuple(newState, null, newStateAction);
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
        String newStateAction = null;
        Order newStateOrder = null;
        long currTime = System.currentTimeMillis();
        long stateTime = stateTuple.getTimestamp();
        switch (stateTuple.getState()) {
        case Init:{
            break;
        }
        case Opening:
        {// 检查是否要超时
            long openTimeout = PBATTR_OPEN_TIMEOUT.getLong(this);
            if (openTimeout > 0 && (currTime - stateTime) >= openTimeout * 1000) {
                newState = PlaybookState.Canceling;
                newStateAction = PBACTION_TIMEOUT;
                newStateOrder = stateTuple.getOrder();
            }
            break;
        }
        case Closing:
        { // 检查是否平仓超时
            long closeTimeout = PBATTR_CLOSE_TIMEOUT.getLong(this);
            if (closeTimeout > 0 && (currTime - stateTime) >= closeTimeout * 1000) {
                newState = PlaybookState.ForceClosing;
                newStateAction = PBACTION_TIMEOUT;
                newStateOrder = stateTuple.getOrder();
            }
            break;
        }
        case Opened:
        {//检查最后持有时间
            newStateAction = canClose(null);
            if ( newStateAction!=null ) {
                newState = PlaybookState.Closing;
            }
            break;
        }
        default:
            break;
        }
        PlaybookStateTuple result = null;
        if ( newState!=null ) {
            result = changeStateTuple(newState, newStateOrder, newStateAction);
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
     * 外部平仓
     * @param order
     * @param txn
     */
    public void closeExternally(Order order, Transaction txn) {
        for(Transaction txn0:order.getTransactions()) {
            updateOnTxn(order, txn0);
        }
        int remaining = getVolume(PBVol.Open);
        if ( 0!=remaining ) {
            addVolume(PBVol.Close, remaining);
            addVolume(PBVol.Pos, -1*remaining);
            setMoney(PBMoney.Close, txn.getPrice());
        }
        changeStateTuple(PlaybookState.Closed, order, "外部平仓");
    }

    /**
     * 切换到新的StateTuple, 这个过程可能会对当前报单有撤销或修改, 或创建新的报单
     *
     * @return 返回旧状态, 如果没有更新, 返回null
     */
    private PlaybookStateTuple changeStateTuple(PlaybookState newState, Order newStateOrder, String actionId)
    {
        PlaybookStateTuple oldStateTuple = stateTuple;
        Account account = group.getAccount();
        OrderAction orderAction = null;
        Order stateOrder = newStateOrder;
        if ( newState!=oldStateTuple.getState() ) {
            switch(newState) {
            case Opening:{ //创建新的Order, 下单操作已经在open()函数中完成.
            }
            break;
            case Canceling:{ //取消当前报单
                try {
                    orderAction = OrderAction.Cancel;
                    account.cancelOrder(stateOrder.getId());
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
                OrderBuilder odrBuilder = createCloseOrderBuilder(account, OrderPriceType.LimitPrice);
                try{
                    stateOrder = account.createOrder(odrBuilder);
                    addVolume(PBVol.Closing, odrBuilder.getVolume());
                    setMoney(PBMoney.Closing, odrBuilder.getLimitPrice());
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
                        orderAction = modifyCloseOrder(account, stateOrder);
                    } else {
                        orderAction = OrderAction.Send;
                        OrderBuilder odrBuilder = createCloseOrderBuilder(account, OrderPriceType.BestPrice);
                        if ( !StringUtil.isEmpty(actionId)) {
                            odrBuilder.setAttr(Order.ODRATTR_PLAYBOOK_ACTION_ID, actionId);
                        }
                        stateOrder = account.createOrder(odrBuilder);
                        addVolume(PBVol.Closing, odrBuilder.getVolume());
                        setMoney(PBMoney.Closing, odrBuilder.getLimitPrice());
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
        if ( null!=stateOrder ) {
            if ( !orderIds.contains(stateOrder.getId())) {
                orders.add(stateOrder);
                orderIds.add(stateOrder.getId());
            }
            pendingOrder = stateOrder;
        }
        PlaybookStateTuple result = null;
        if ( newState!=oldStateTuple.getState()) {
            MarketTimeService mtService = group.getMarketTimeService();
            PlaybookStateTupleImpl newStateTuple = new PlaybookStateTupleImpl(mtService, newState, stateOrder, orderAction, actionId);
            this.stateTuples.add(newStateTuple);
            this.stateTuple = newStateTuple;
            result = oldStateTuple;
            //异步保存自身状态
            group.getRepository().asynSave(BOEntityType.Playbook, getId(), this);
        }
        return result;
    }

    /**
     * 在开仓后, 检查是否可以平仓
     */
    private String canClose(MarketData tick) {
        String result = null;
//        MarketTimeService mtService = group.getBeansContainer().getBean(MarketTimeService.class);
//        long maxLifeTime = 0;
//        if ( result==null && (maxLifeTime = PBATTR_MAX_LIFETIME.getSecond(this))>0 ) {
//            long currMillis = mtService.currentTimeMillis();
//            if ( (currMillis-getStateTuple().getTimestamp())>maxLifeTime*1000 ) {
//                result = PBACTION_MAXLIFETIME;
//            }
//        }
//        LocalDateTime endTime = null;
//        if ( result==null && (endTime = PBATTR_END_TIME.getDateTime(this))!=null ) {
//            LocalDateTime mtTime = mtService.getMarketTime();
//            if ( mtTime.isAfter(endTime)) {
//                result = PBACTION_ENDTIME;
//            }
//        }
//        long simplePriceAbove = 0;
//        if ( result==null && tick!=null && (simplePriceAbove = PBATTR_SIMPLE_PRICE_ABOVE.getPrice(this))>0 ) {
//            if ( tick.lastPrice>= simplePriceAbove ) {
//                result = PBACTION_SIMPLE_PRICE_ABOVE+" "+PriceUtil.long2str(simplePriceAbove);
//            }
//        }
//        long simplePriceBelow = 0;
//        if ( result==null && tick!=null && (simplePriceBelow = PBATTR_SIMPLE_PRICE_BELOW.getPrice(this))>0 ) {
//            if ( tick.lastPrice<= simplePriceBelow ) {
//                result = PBATTR_SIMPLE_PRICE_BELOW+" "+PriceUtil.long2str(simplePriceBelow);
//            }
//        }
        return result;
    }

    /**
     * 创建平仓报单
     * <BR>暂时不考虑滑点
     */
    private OrderBuilder createCloseOrderBuilder(Account account, OrderPriceType priceType) {
        MarketDataService mdService = group.getMarketDataService();
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
        int volToClose = getVolume(PBVol.Pos);
        if ( volToClose<=0 ) {
            volToClose = Math.max(getVolume(PBVol.Opening), getVolume(PBVol.Open));
        }
        OrderBuilder odrBuilder = new OrderBuilder()
            .setExchagneable(instrument)
            .setVolume(volToClose)
            .setAttr(Order.ODRATTR_PLAYBOOK_ID, id)
            .setAttr(Order.ODRATTR_TRADLET_GROUP_ID, group.getId())
            .setLimitPrice(closePrice)
            .setPriceType(priceType)
            .setDirection(odrDirection)
            .setOffsetFlag(odrOffsetFlag);

        return odrBuilder;
    }

    /**
     * 强制关闭报单
     */
    private OrderAction modifyCloseOrder(Account account, Order order) throws AppException
    {
        OrderAction result = null;
        if ( !order.getStateTuple().getState().isDone() ) {
            MarketDataService mdService = group.getMarketDataService();
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

            account.modifyOrder(order.getId(), odrBuilder);
        }
        return result;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = super.toJson().getAsJsonObject();
        json.addProperty("groupId", groupId);
        json.addProperty("direction", direction.name());
        json.addProperty("openDirection", openDirection.name());
        json.addProperty("state", stateTuple.getState().name());
        json.add("stateTuples", JsonUtil.object2json(stateTuples));
        json.add("volumes",  TradletConstants.pbVolume2json(volumes));
        json.add("money",  TradletConstants.pbMoney2json(money));
        if( attrs!=null ) {
            JsonObject attrsJson = new JsonObject();
            for(String attr:attrs.keySet()) {
                Object val = attrs.get(attr);
                attrsJson.add(attr, JsonUtil.object2json(val, false));
            }
            json.add("attrs", attrsJson);
        }
        json.addProperty("version", version);
        json.add("orderIds", JsonUtil.object2json(orderIds));
        if ( pendingOrder!=null ) {
            json.addProperty("pendingOrderId", pendingOrder.getId());
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

    public static PlaybookImpl load(BORepository repository, String pbId, String pbData) {
        PlaybookImpl result = (PlaybookImpl)cacheGet(pbId);
        if ( null==result ) {
            String json = pbData;
            if (null==json) {
                json = repository.load(BOEntityType.Playbook, pbId);
            }
            if ( !StringUtil.isEmpty(json) ) {
                result = new PlaybookImpl(repository, (JsonObject)JsonParser.parseString(json));
                cachePut(result);
            }
        }
        return result;
    }

    /**
     * 加载当日或非当日且未结束的交易剧本
     */
    public static List<PlaybookImpl> loadAll(BORepository repository, String accountId, String groupId, LocalDate tradingDay){
        StringBuilder queryExpr = new StringBuilder();
        SQLListExpr listExpr = new SQLListExpr();
        for(var state:PlaybookState.values()) {
            if (state.isDone()) {
                listExpr.addItem(new SQLCharExpr(state.name()));
            }
        }
        queryExpr.append("accountId=").append(new SQLCharExpr(accountId));
        queryExpr.append(" AND (groupId=").append(new SQLCharExpr(groupId)).append(")");
        queryExpr.append(" AND (state NOT IN ").append(listExpr).append(" OR tradingDay=").append(new SQLCharExpr(DateUtil.date2str(tradingDay))).append(")");
        BOEntityIterator entityIt = repository.search(BOEntityType.Playbook, queryExpr.toString());
        List<PlaybookImpl> result = new ArrayList<>();
        while(entityIt.hasNext()) {
            String pbId=entityIt.next();
            String pbData = entityIt.getData();
            PlaybookImpl pb = load(repository, pbId, pbData);
            result.add(pb);
        }
        return result;
    }

}
