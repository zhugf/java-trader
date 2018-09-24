package trader.common.tick;

import java.time.LocalDateTime;
import java.time.ZoneId;

import trader.common.util.DateUtil;

/**
 * 在某个时刻的价量信息
 */
public class Tick implements Comparable<Tick> {

    protected long time;
    protected long price;
    protected long volume;
    protected long turnover;

    protected Tick(){
    }

    public Tick(long time, long price){
        this(time, price, 0, 0);
    }

    public Tick(long time, long price, long volume, long turnover){
        this.time = time;
        this.price = price;
        this.volume = volume;
        this.turnover = turnover;
    }

    public long getTime(){
        return time;
    }

    public long getPrice(){
        return price;
    }

    protected void setPrice(long price){
        this.price = price;
    }

    public long getTurnover(){
        return turnover;
    }

    protected void setTurnover(long v){
        this.turnover = v;
    }

    public long getVolume(){
        return volume;
    }

    protected void setVolume(long v){
        this.volume = v;
    }

    protected void setTime(long t)
    {
        time = t;
    }

    public boolean isLower(Tick o){
        if ( o==null ){
            return false;
        }
        return getPrice()<o.getPrice();
    }

    public boolean isHigher(Tick o){
        if ( o==null ){
            return false;
        }
        return getPrice()>o.getPrice();
    }

    public boolean isLowOrEquals(Tick o){
        if ( o==null ){
            return false;
        }
        return getPrice()<=o.getPrice();
    }

    public boolean isHighOrEquals(Tick o){
        if ( o==null ){
            return false;
        }
        return getPrice()>=o.getPrice();
    }

    public boolean isBefore(Tick o){
        //assert(getTime().toLocalDate().equals(o.getTime().toLocalDate()));
        return getTime()<(o.getTime());
    }

    public boolean isAfter(Tick o){
        //assert(getTime().toLocalDate().equals(o.getTime().toLocalDate()));
        return getTime()>(o.getTime());
    }

    @Override
    public int compareTo(Tick o)
    {
        return (int)(getPrice()-o.getPrice());
    }

    @Override
    public boolean equals(Object o){
        if ( o==null || !(o instanceof Tick) ){
            return false;
        }
        Tick p = (Tick)o;
        return time==(p.time)
                && price==p.price
                && volume==p.volume
                && turnover==p.turnover;
    }

    @Override
	public String toString() {
    	return toString(DateUtil.getDefaultZoneId());
    }

    public String toString(ZoneId zoneId){
    	LocalDateTime dateTime = DateUtil.long2datetime(zoneId, getTime());
        return "Point["+dateTime+":"+getPrice()+"]";
    }

    public static Tick max(Tick ...points){
        Tick r = points[0];
        for(Tick p:points){
            if ( r.isLower(p)){
                r = p;
            }
        }
        return r;
    }

    public static Tick min(Tick ...points){
        Tick r = points[0];
        for(Tick p:points){
            if ( r.isHigher(p)){
                r = p;
            }
        }
        return r;
    }

    public Tick changePrice(long newPrice){
        Tick result = new Tick(getTime(), newPrice, getVolume(), getTurnover());
        return result;
    }

    public Tick toPoint(){
        Tick result = new Tick( getTime(), getPrice() );
        return result;
    }

}
