package trader.service.repository;

import java.io.Closeable;
import java.util.Iterator;

public interface BOEntityIterator extends Iterator<String>, Closeable {

    public String getValue();

}
