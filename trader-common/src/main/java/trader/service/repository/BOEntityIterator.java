package trader.service.repository;

import java.io.Closeable;
import java.util.Iterator;

public interface BOEntityIterator extends Iterator<String>, Closeable {

    /**
     * 返回原始的JSON数据
     */
    public String getData();

    public Object getEntity();
}
