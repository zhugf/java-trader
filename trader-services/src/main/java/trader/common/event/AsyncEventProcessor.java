package trader.common.event;

/**
  * 异步数据处理
 *
 */
public interface AsyncEventProcessor {

	public void process(int dataType, Object data, Object data2);

}
