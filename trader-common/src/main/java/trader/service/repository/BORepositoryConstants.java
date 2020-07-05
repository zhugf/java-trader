package trader.service.repository;

public interface BORepositoryConstants {

    /**
     * 组成ID的多个部分的分隔符
     */
    public static char ID_PART_SEPARATOR = ':';

    public static final String ID_PREFIX_PLAYBOOK = "plb_";
    public static final String ID_PREFIX_ORDER = "odr_";
    public static final String ID_PREFIX_TRANSACTION = "txn_";

    public static enum BOEntityType{
        Default
        ,Order
        ,Playbook
        ,Transaction
    }

}
