package trader.common.exception;

import org.json.JSONObject;

@SuppressWarnings("serial")
public class AppRuntimeException extends RuntimeException implements AppThrowable {

    private int code;

    public AppRuntimeException(AppThrowable cause) {
        this((Throwable)cause, cause.getCode(), cause.getMessage());
    }

    public AppRuntimeException(int code, String message) {
        this(null, code, message);
    }

    public AppRuntimeException(Throwable cause, int code, String message) {
        super(message, cause);
        this.code = code;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public StackTraceElement getCallerStackTrace() {
        for(StackTraceElement elem: getStackTrace()) {
            if ( elem.getClassName().equalsIgnoreCase( getClass().getName() )) {
                continue;
            }
            return elem;
        }
        return null;
    }

    @Override
    public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();
        obj.put("errorCode", code);
        obj.put("errorMessage", getMessage());
        return obj;
    }
}
