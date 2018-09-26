package trader.service.log;

public interface LogListener {

    public void onSetLevel(String category, String levelStr);

}
