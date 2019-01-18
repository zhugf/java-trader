package trader.service.tradlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exception.AppException;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.UUIDUtil;
import trader.service.trade.Account;
import trader.service.trade.Order;
import trader.service.trade.OrderBuilder;
import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;
import trader.service.trade.TradeConstants.OrderPriceType;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.TradletConstants.PlaybookState;

public class PlaybookKeeperImpl implements PlaybookKeeper, JsonEnabled {
    private static final Logger logger = LoggerFactory.getLogger(PlaybookKeeperImpl.class);

    private TradletGroupImpl group;
    private Account account;
    private List<Order> allOrders = new ArrayList<>();
    private LinkedList<Order> pendingOrders = new LinkedList<>();
    private LinkedHashMap<String, PlaybookImpl> allPlaybooks = new LinkedHashMap<>();
    private List<PlaybookImpl> activePlaybooks = new LinkedList<>();

    public PlaybookKeeperImpl(TradletGroupImpl group, Account account) {
        this.group = group;
        this.account = account;
    }

    @Override
    public List<Order> getAllOrders() {
        return allOrders;
    }

    @Override
    public List<Order> getPendingOrders() {
        return pendingOrders;
    }

    @Override
    public Order getLastOrder() {
        if ( allOrders.isEmpty() ) {
            return null;
        }
        return allOrders.get(allOrders.size()-1);
    }

    @Override
    public Order getLastPendingOrder() {
        if ( pendingOrders.isEmpty() ) {
            return null;
        }
        return pendingOrders.getLast();
    }

    @Override
    public void cancelAllPendingOrders() {
        for(Order order:pendingOrders) {
            if ( order.getStateTuple().getState().isRevocable() ) {
                try {
                    account.cancelOrder(order.getRef());
                } catch (AppException e) {
                    logger.error("Tradlet group "+group.getId()+" cancel order "+order.getRef()+" failed "+e.toString(), e);
                }
            }
        }
    }

    @Override
    public Collection<Playbook> getAllPlaybooks() {
        return (Collection)allPlaybooks.values();
    }

    @Override
    public Collection<Playbook> getActivePlaybooks() {
        return (Collection)activePlaybooks;
    }

    @Override
    public Playbook getPlaybook(String playbookId) {
        return allPlaybooks.get(playbookId);
    }

    @Override
    public void createPlaybook(PlaybookBuilder builder) throws AppException {
        String playbookId = "plb_"+UUIDUtil.genUUID58();
        OrderBuilder odrBuilder = new OrderBuilder(account);
        odrBuilder.setExchagneable(group.getExchangeable())
            .setDirection(builder.getOpenDirection()==PosDirection.Long?OrderDirection.Buy:OrderDirection.Sell)
            .setLimitPrice(builder.getOpenPrice())
            .setPriceType(OrderPriceType.LimitPrice)
            .setVolume(builder.getVolume())
            .setOffsetFlag(OrderOffsetFlag.OPEN)
            .setAttr(Playbook.ATTR_PLAYBOOK_ID, playbookId);

        Order order = account.createOrder(odrBuilder);

        PlaybookImpl playbook = new PlaybookImpl(playbookId, builder, new PlaybookStateTupleImpl(PlaybookState.Openning, order));
        allPlaybooks.put(playbookId, playbook);
        activePlaybooks.add(playbook);
    }

    /**
     * 更新订单状态
     */
    public void updateOnOrder(Order order) {
        String playbookId = order.getAttr(Playbook.ATTR_PLAYBOOK_ID);
        PlaybookImpl playbook = allPlaybooks.get(playbookId);
        if ( playbook==null ) {
            return;
        }
//        OrderState odrState = order.getStateTuple().getState();
//        Playbook.State newState = null;
//        switch(playbook.getState()) {
//        case Openning:
//            switch (odrState) {
//            case Failed:
//                newState = Playbook.State.Failed;
//                break;
//            }
//            break;
//        case Opened:
//            break;
//        case Closing:
//            break;
//        }

    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("allOrderCount", allOrders.size());
        json.addProperty("pendingOrderCount", pendingOrders.size());
        json.addProperty("allPlaybookCount", allPlaybooks.size());
        json.add("activePlaybooks", JsonUtil.object2json(activePlaybooks));
        return json;
    }

}
