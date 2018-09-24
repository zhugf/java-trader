package trader.common.util;

import java.io.IOException;

@FunctionalInterface
public interface IOAction<T> {

	public T doAction() throws IOException;
}
