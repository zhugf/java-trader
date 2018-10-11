package trader.service;

public interface ServiceErrorConstants {

    public static final int ERRCODE_TRADE = 0X00010000;

    //报单的错误
    public static final int ERRCODE_TRADE_VOL_EXCEEDS_LIMIT                 = 0X00010001;
    public static final int ERRCODE_TRADE_VOL_OPEN_NOT_ENOUGH               = 0X00010002;
    public static final int ERRCODE_TRADE_EXCHANGEABLE_INVALID              = 0X00010003;
}
