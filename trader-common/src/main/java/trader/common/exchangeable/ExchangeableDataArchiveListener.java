package trader.common.exchangeable;

public interface ExchangeableDataArchiveListener {
    public void onArchiveBegin(Exchangeable e);
    public void onArchiveEnd(Exchangeable e, int archivedFileCount);

    public void onArchiveBegin(String subDir);
    public void onArchiveEnd(String subDir, int archivedFileCount);
}
