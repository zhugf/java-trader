package trader.simulator.trade;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.trade.TradeConstants;
import trader.simulator.trade.SimOrder.SimOrderState;

/**
 * 模拟交易所持仓
 */
public class SimPosition implements JsonEnabled, TradeConstants {

    private Exchangeable e;
    private SimTxnSession session;
    private PosDirection direction;
    private long[] money = new long[PosMoney.values().length];
    private int[] volumes = new int[PosVolume.values().length];
    /**
     * 持仓明细
     */
    private List<SimPositionDetail> details = new LinkedList<>();
    /**
     * 平仓报单
     */
    private List<SimOrder> orders = new LinkedList<>();

    SimPosition(SimTxnSession session, Exchangeable e){
        this.session = session;
        this.e = e;
    }

    public Exchangeable getExchangeable() {
        return e;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("direction", getDirection().name());
        json.add("volumes", TradeConstants.posVolume2json(volumes));
        json.add("money", TradeConstants.posMoney2json(money));
        return json;
    }

    public PosDirection getDirection() {
        return direction;
    }

    public int getVolume(PosVolume vol) {
        return volumes[vol.ordinal()];
    }

    public long getMoney(PosMoney mny) {
        return money[mny.ordinal()];
    }

    public List<SimPositionDetail> getDetails(){
        return details;
    }

    public List<SimOrder> getOrders(){
        return orders;
    }

    public SimOrder getOrder(String ref) {
        for(SimOrder order:orders) {
            if ( StringUtil.equals(ref, order.getRef())) {
                return order;
            }
        }
        return null;
    }

    public void addOrder(SimOrder order){
        if ( !orders.contains(order)) {
            orders.add(order);
        }
    }

    public SimOrder removeOrder(String ref) {
        for(Iterator<SimOrder> it=orders.iterator(); it.hasNext();) {
            SimOrder o=it.next();
            if ( o.getRef().equals(ref)) {
                return o;
            }
        }
        return null;
    }

    /**
     * 有成交时更新
     */
    public void updateOnTxn(SimTxn txn, LocalDateTime time) {
        if(  txn==null ) {
            return;
        }
        long txnProfit = 0;
        SimOrder order = txn.getOrder();
        orders.remove(order);

        //手续费
        long orderValues[] = session.getFeeEvaluator().compute(e, txn.getVolume(), txn.getPrice(), order.getDirection(), order.getOffsetFlag());
        money[PosMoney.Commission.ordinal()] += orderValues[1];

        //修改持仓
        if ( order.getOffsetFlag()==OrderOffsetFlag.OPEN ) {
            //开
            SimPositionDetail detail = new SimPositionDetail(txn.getDirection().toPosDirection(), txn.getVolume(), txn.getPrice(), time);
            details.add(detail);
            volumes[PosVolume.OpenVolume.ordinal()] += txn.getVolume();
        }else {
            //平
            volumes[PosVolume.CloseVolume.ordinal()] += txn.getVolume();

            List<SimPositionDetail> pdsToClose = new ArrayList<>();
            int volumeLeft = txn.getVolume();
            int lastPartCloseVolume=0;
            SimPositionDetail lastPartClosePd = null;
            PosDirection pdDir = PosDirection.fromOrderDirection(txn.getDirection()).oppose();
            for(Iterator<SimPositionDetail> pdIt=details.iterator();pdIt.hasNext();){
                SimPositionDetail pd = pdIt.next();
                if ( pd.getVolume()==0 || !pd.getDirection().equals(pdDir) ) {
                    continue;
                }
                if ( pd.getVolume()<volumeLeft ){
                    volumeLeft -= pd.getVolume();
                    pdsToClose.add(pd);
                    pdIt.remove();
                    continue;
                }
                if ( pd.getVolume()==volumeLeft ){
                    volumeLeft=0;
                    pdsToClose.add(pd);
                    pdIt.remove();
                    break;
                }
                if ( pd.getVolume()>volumeLeft ){
                    lastPartCloseVolume = volumeLeft;
                    lastPartClosePd = pd;
                    volumeLeft=0;
                    pdsToClose.add(pd);
                    break;
                }
                if ( volumeLeft==0 ) {
                    break;
                }
            }
            txnProfit = computeCloseProfit(pdsToClose, lastPartCloseVolume, txn);
            if ( lastPartClosePd!=null ){
                lastPartClosePd.setVolume(lastPartClosePd.getVolume()-lastPartCloseVolume);
            }
            money[PosMoney.CloseProfit.ordinal()] += txnProfit;
        }

    }

    /**
     * 市场价格更新
     */
    public void updateOnMarketData(MarketData md) {
        long posProfit=0, longMargin = 0, shortMargin=0, longFrozenMargin=0, shortFrozenMargin=0, frozenCommission=0;
        long lastPrice = md.lastPrice;
        int longPos = 0, shortPos = 0, longTodayPos=0, longYdPos = 0, shortTodayPos=0, shortYdPos=0;
        int longFrozenPos=0, shortFrozenPos=0;
        LocalDate tradingDay = session.getTradingDay();
        for(SimPositionDetail d:details) {
            long[] posOpenValues = session.getFeeEvaluator().compute(e, d.getVolume(), d.getOpenPrice(), d.getDirection());
            long[] posValues = session.getFeeEvaluator().compute(e, d.getVolume(), lastPrice, d.getDirection());
            if ( d.getDirection()==PosDirection.Long ) {
                longPos+=d.getVolume();
                if ( tradingDay.equals(d.getOpenTime().toLocalDate()) ) {
                    longTodayPos += d.getVolume();
                }else {
                    longYdPos += d.getVolume();
                }
                longMargin += posValues[0];
            }else {
                shortPos+=d.getVolume();
                if ( tradingDay.equals(d.getOpenTime().toLocalDate()) ) {
                    shortTodayPos += d.getVolume();
                }else {
                    shortYdPos += d.getVolume();
                }
                shortMargin += posValues[0];
            }
            posProfit += (posValues[1] - posOpenValues[1]);
        }
        for(SimOrder o:orders) {
            if ( o.getOffsetFlag()==OrderOffsetFlag.OPEN ) {
                long[] orderValues = session.getFeeEvaluator().compute(e, o.getVolume(), o.getLimitPrice(), o.getDirection(), o.getOffsetFlag());
                frozenCommission += orderValues[1];
                //开仓
                if ( o.getDirection()==OrderDirection.Buy ) {
                    //开多
                    longFrozenMargin += orderValues[0];
                }else {
                    //开空
                    shortFrozenMargin += orderValues[0];
                }
            } else {
                if ( o.getDirection()==OrderDirection.Sell ) {
                    //平多
                    longFrozenPos += o.getVolume();
                }else {
                    //平空
                    shortFrozenPos += o.getVolume();
                }
            }
        }
        direction = PosDirection.Net;
        if ( longPos>shortPos) {
            direction = PosDirection.Long;
        }
        if ( shortPos>longPos) {
            direction = PosDirection.Short;
        }
        volumes[PosVolume.Position.ordinal()] = longPos+shortPos;
        //volumes[PosVolume.OpenVolume]
        //volumes[PosVolume.CloseVolume]
        volumes[PosVolume.LongFrozen.ordinal()] = longFrozenPos;
        volumes[PosVolume.ShortFrozen.ordinal()] = shortFrozenPos;
        volumes[PosVolume.TodayPosition.ordinal()] = longTodayPos+shortTodayPos;
        volumes[PosVolume.YdPosition.ordinal()] = longYdPos+shortYdPos;
        volumes[PosVolume.LongPosition.ordinal()] = longPos;
        volumes[PosVolume.ShortPosition.ordinal()] = shortPos;
        volumes[PosVolume.LongTodayPosition.ordinal()] = longTodayPos;
        volumes[PosVolume.ShortTodayPosition.ordinal()] = shortTodayPos;
        volumes[PosVolume.LongYdPosition.ordinal()] = longYdPos;
        volumes[PosVolume.ShortYdPosition.ordinal()] = shortYdPos;

        money[PosMoney.LongFrozenAmount.ordinal()] = longFrozenMargin;
        money[PosMoney.ShortFrozenAmount.ordinal()] = shortFrozenMargin;
        money[PosMoney.FrozenMargin.ordinal()] = longFrozenMargin+shortFrozenMargin;;
        money[PosMoney.FrozenCommission.ordinal()] = frozenCommission;
        money[PosMoney.PositionProfit.ordinal()] = posProfit;
        money[PosMoney.UseMargin.ordinal()] = Math.max(longMargin, shortMargin);
        money[PosMoney.LongUseMargin.ordinal()] = longMargin;
        money[PosMoney.ShortUseMargin.ordinal()] = shortMargin;
    }

    public int removeCompleteOrders() {
        int count=0;
        for(Iterator<SimOrder> it=orders.iterator();it.hasNext();) {
            SimOrder o = it.next();
            if ( o.getState()!=SimOrderState.Placed ) {
                it.remove();
                count++;
            }
        }
        return count;
    }

    private long computeCloseProfit(List<SimPositionDetail> pds, int lastPartCloseVolume, SimTxn t)
    {
        long totalCloseProfit = 0;
        for(int i=0;i<pds.size();i++){
            boolean lastPd = i==pds.size()-1;
            SimPositionDetail pd = pds.get(i);
            int volumeToClose = pd.getVolume();
            if ( lastPd&&lastPartCloseVolume>0 ){
                volumeToClose = lastPartCloseVolume;
            }
            long pdOpenValue[] = session.getFeeEvaluator().compute(e, volumeToClose, pd.getOpenPrice(), direction);
            long pdCloseValue[] = session.getFeeEvaluator().compute(e, volumeToClose, t.getPrice(), direction);
            long pdCloseProfit = 0;
            switch(t.getDirection()){
            case Sell:
                pdCloseProfit = pdCloseValue[1]-pdOpenValue[1];
                break;
            case Buy:
                pdCloseProfit = pdOpenValue[1]-pdCloseValue[1];
                break;
            }
            totalCloseProfit += pdCloseProfit;
        }
        return totalCloseProfit;
    }
}
