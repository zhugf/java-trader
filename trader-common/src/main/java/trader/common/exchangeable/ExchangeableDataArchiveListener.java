package trader.common.exchangeable;

import java.io.File;
import java.util.List;

public interface ExchangeableDataArchiveListener {
    public void onArchiveBegin(Exchangeable e, File edir);
    public void onArchiveEnd(Exchangeable e, List<String> archivedFiles);

    public void onArchiveBegin(File subDir);
    public void onArchiveEnd(File subDir, List<String> archivedFiles);
}
