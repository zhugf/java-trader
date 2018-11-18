package trader.common.exception;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
    public JsonElement toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("errorCode", code);
        obj.addProperty("errorMessage", getMessage());
        return obj;
    }

}
