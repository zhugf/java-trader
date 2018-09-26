package trader.service.log;

public class LogLevelInfo {
    private String level;
    private boolean inherited;
    public String getLevel() {
        return level;
    }
    public void setLevel(String level) {
        this.level = level;
    }
    public boolean isInherited() {
        return inherited;
    }
    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

}
