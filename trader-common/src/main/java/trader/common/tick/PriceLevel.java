package trader.common.tick;

public enum PriceLevel {

    TICKET(-1)
    , MIN1(1)
    , MIN3(3)
    , MIN5(5)
    , MIN15(15)
    , HOUR(60)
    , DAY(-1)
    , WEEK(-1)
    , MONTH(-1)
    , QUARTER(-1)
    , YEAR(-1)
    ;

    private PriceLevel(int minutePeriod){
        this.minutePeriod = minutePeriod;
    }

    private int minutePeriod;

    public int getMinutePeriod(){
        return minutePeriod;
    }

    public PriceLevel levelUp(){
        if ( this.ordinal()== values().length-1 ){
            throw new RuntimeException(this+" is the largest level");
        }
        return values()[ordinal()+1];
    }

    public PriceLevel levelDown(){
        if ( this.ordinal()==0 ){
            throw new RuntimeException(this+" is the least level");
        }
        return values()[ordinal()-1];
    }

    public boolean isUpperLevel(PriceLevel level){
        return ordinal() > level.ordinal();
    }

    public static PriceLevel parse(String str){
    	for(PriceLevel l:values()){
    		if ( l.name().equalsIgnoreCase(str.trim())){
    			return l;
    		}
    	}
    	return null;
    }

    public static PriceLevel minute2level(int minute){
        switch(minute){
        case 1:
            return PriceLevel.MIN1;
        case 3:
            return PriceLevel.MIN3;
        case 5:
            return PriceLevel.MIN5;
        case 15:
            return PriceLevel.MIN15;
        case 60:
            return PriceLevel.HOUR;
        default:
            throw new RuntimeException("Unsupported minute level: "+minute);
        }
    }
}
