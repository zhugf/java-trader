package trader.common.tick;

/**
 * 一段时间的价量数据
 */
public class Bar {

	protected long openPrice;

	protected long closePrice;

	protected long highPrice;

	protected long lowPrice;

	protected long openTime;

	protected long closeTime;

	protected int duration;

	protected long volume;

	protected long turnover;

	public long getOpenPrice() {
		return openPrice;
	}

	public long getClosePrice() {
		return closePrice;
	}

	public long getHighPrice() {
		return highPrice;
	}

	public long getLowPrice() {
		return lowPrice;
	}

	public long getOpenTime() {
		return openTime;
	}

	public long getCloseTime() {
		return closeTime;
	}

	public int getDuration() {
		return duration;
	}

	public long getVolume() {
		return volume;
	}

	public long getTurnover() {
		return turnover;
	}


}
