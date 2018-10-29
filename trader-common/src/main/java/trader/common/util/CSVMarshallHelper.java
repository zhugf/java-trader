package trader.common.util;

public interface CSVMarshallHelper<T> {

	public String[] getHeader();

	public T unmarshall(String[] row);

	public String[] marshall(T t);
}
