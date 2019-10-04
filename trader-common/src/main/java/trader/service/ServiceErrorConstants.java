package trader.service;

public interface ServiceErrorConstants {

    public static final int SERVICE_TRADE               = 0X00010000;
    public static final int SERVICE_MD                  = 0X00020000;
    public static final int SERVICE_TA                  = 0X00030000;
    public static final int SERVICE_DATA                = 0X00040000;
    public static final int SERVICE_TRADLET             = 0X00050000;
    public static final int SERVICE_PLUGIN              = 0X00060000;

    public static final int ERRCODE_PLUGIN_NOT_FOUND                     = SERVICE_PLUGIN|0X0001;

    //报单的错误
    public static final int ERRCODE_TRADE_VOL_EXCEEDS_LIMIT             = SERVICE_TRADE|0X0001;
    public static final int ERRCODE_TRADE_VOL_OPEN_NOT_ENOUGH           = SERVICE_TRADE|0X0002;
    public static final int ERRCODE_TRADE_EXCHANGEABLE_INVALID          = SERVICE_TRADE|0X0003;
    public static final int ERRCODE_TRADE_MARGIN_NOT_ENOUGH             = SERVICE_TRADE|0X0004;
    public static final int ERRCODE_TRADE_SEND_ORDER_FAILED             = SERVICE_TRADE|0X0005;
    public static final int ERRCODE_TRADE_ORDER_NOT_FOUND               = SERVICE_TRADE|0X0006;
    public static final int ERRCODE_TRADE_SESSION_NOT_READY             = SERVICE_TRADE|0X0007;
    public static final int ERRCODE_TRADE_MODIFY_ORDER_FAILED           = SERVICE_TRADE|0X0008;
    public static final int ERRCODE_TRADE_CANCEL_ORDER_FAILED           = SERVICE_TRADE|0X0009;
    public static final int ERRCODE_TRADE_INVALID_ORDER                 = SERVICE_TRADE|0X000A;

    //行情错误
    public static final int ERR_MD_PRODUCER_CREATE_FAILED               = SERVICE_MD|0X0001;
    public static final int ERR_MD_PRODUCER_DISCONNECTED                = SERVICE_MD|0X0002;
    public static final int ERR_MD_PRODUCER_CONNECT_FAILED              = SERVICE_MD|0X0003;

    //数据错误
    public static final int ERR_DATA_LOAD_FAILED                        = SERVICE_DATA|0X0001;

    //交易小程序错误
    public static final int ERR_TRADLET_TRADLET_NOT_FOUND               = SERVICE_TRADLET|0X0001;
    public static final int ERR_TRADLET_TRADLET_CREATE_FAILED           = SERVICE_TRADLET|0X0002;
    public static final int ERR_TRADLET_TRADLETGROUP_UPDATE_FAILED      = SERVICE_TRADLET|0X0003;
    public static final int ERR_TRADLET_TRADLETGROUP_INVALID_CONFIG     = SERVICE_TRADLET|0X0004;
    public static final int ERR_TRADLET_INVALID_ACCOUNT_VIEW            = SERVICE_TRADLET|0X0005;
    public static final int ERR_TRADLET_INVALID_EXCHANGEABLE            = SERVICE_TRADLET|0X0006;
    public static final int ERR_TRADLET_TRADLETGROUP_NOT_ENABLED        = SERVICE_TRADLET|0X0007;
    public static final int ERR_TRADLET_INVALID_INSTRUMENT              = SERVICE_TRADLET|0X0008;
    public static final int ERR_TRADLET_STOP_SETTINGS_INVALID           = SERVICE_TRADLET|0X0009;

}
