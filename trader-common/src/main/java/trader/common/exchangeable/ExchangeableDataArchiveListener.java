package trader.common.exchangeable;

import java.io.File;

public interface ExchangeableDataArchiveListener {
    public void onArchiveBegin(Exchangeable e, File edir);
    public void onArchiveEnd(Exchangeable e, int archivedFileCount);

    public void onArchiveBegin(File subDir);
    public void onArchiveEnd(File subDir, int archivedFileCount);
}
