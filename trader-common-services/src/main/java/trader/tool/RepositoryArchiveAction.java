package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableDataArchiveListener;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.DateUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.util.CmdAction;

public class RepositoryArchiveAction implements CmdAction, ExchangeableDataArchiveListener {

    private PrintWriter writer;
    private List<File> archivedFiles = new ArrayList<>();
    private ThreadPoolExecutor executorService;

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
        executorService = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        ExchangeableData exchangeableData = TraderHomeUtil.getExchangeableData();;
        this.writer = writer;
        exchangeableData.archive(executorService, this);
        return 0;
    }

    @Override
    public void onArchiveBegin(Exchangeable e, File edir) {
    }

    @Override
    public synchronized void onArchiveEnd(Exchangeable e, File edir, List<String> archivedFiles) {
        writer.println(DateUtil.date2str(LocalDateTime.now())+" 归档 "+e+" 目录 "+edir+"("+archivedFiles.size()+") : "+archivedFiles); writer.flush();
    }

    @Override
    public void onArchiveBegin(File subDir) {
    }

    @Override
    public void onArchiveEnd(File subDir, List<String> archivedFiles) {
    }

}
