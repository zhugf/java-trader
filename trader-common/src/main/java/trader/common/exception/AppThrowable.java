package trader.common.exception;

import trader.common.util.JsonEnabled;

public interface AppThrowable extends JsonEnabled {

    public int getCode();

    public String getMessage();

    public StackTraceElement getCallerStackTrace();

    public static String getCallerClass() {
        Exception e = new Exception();
        for(StackTraceElement elem: e.getStackTrace()) {
            String className = elem.getClassName();
            if ( !className.equals(AppThrowable.class.getName())
                 && !className.equals(AppException.class.getName())
                 && !className.equals(AppRuntimeException.class.getName())
                 )
            {
                return className;
            }
        }
        return null;
    }

    public static String errorcode2str(int errorCode) {
        return String.format("%08X", errorCode).toUpperCase();
    }
}
