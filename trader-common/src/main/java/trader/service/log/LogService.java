package trader.service.log;

public interface LogService {

    public LogLevelInfo getLevel(String category);

    public void setLevel(String category, String levelStr, boolean propagate);

    public void setListener(LogListener listener);
}
