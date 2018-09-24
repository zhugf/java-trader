package trader.common.beans;

import java.lang.annotation.*;

/**
 * 标志某个实现类会被自动扫描并加载
 */
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface Discoverable {
    /**
     * 接口
     */
    public Class interfaceClass();

    /**
     * 用途, 必须唯一
     */
    public String purpose() default "";
}
