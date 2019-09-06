package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableDataArchiveListener;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.service.util.CmdAction;

public class RepositoryArchiveAction implements CmdAction, ExchangeableDataArchiveListener {

    PrintWriter writer;

    @Override
    public String getCommand() {
        return "repository.archive";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("repository archive");
        writer.println("\t压缩存档已导入的数据");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        ExchangeableData exchangeableData = TraderHomeUtil.getExchangeableData();;
        this.writer = writer;
        exchangeableData.archive(this);
        return 0;
    }

    @Override
    public void onArchiveBegin(Exchangeable e, File edir) {
        writer.print("归档 "+e+" 目录: "+edir+" ... "); writer.flush();
    }

    @Override
    public void onArchiveEnd(Exchangeable e, int archivedFileCount) {
        writer.println("完成("+archivedFileCount+")"); writer.flush();
    }

    @Override
    public void onArchiveBegin(File subDir) {
    }

    @Override
    public void onArchiveEnd(File subDir, int archivedFileCount) {
    }

}
