package trader.service.ta;

import java.util.function.Function;

import org.ta4j.core.num.Num;

/**
 * long表示价格, 4位小数
 */
public class LongNum implements Num {
    private static final long serialVersionUID = -6389115676116240242L;

    public static final LongNum ZERO = new LongNum(0);

    private long value;

    public LongNum(long value) {
        this.value = value;
    }

    @Override
    public int compareTo(Num o) {
        return Long.compare(value, ((LongNum)o).value);
    }

    @Override
    public Number getDelegate() {
        return Long.valueOf(value);
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public Num plus(Num augend) {
        long result = value+((LongNum)augend).value;
        return new LongNum(result);
    }

    @Override
    public Num minus(Num subtrahend) {
        long result = value-((LongNum)subtrahend).value;
        return new LongNum(result);
    }

    @Override
    public Num multipliedBy(Num multiplicand) {
        return new LongNum(value*((LongNum)multiplicand).value);
    }

    @Override
    public Num dividedBy(Num divisor) {
        if ( divisor.isZero() ) {
            return new LongNum(Long.MAX_VALUE);
        }
        return new LongNum(value/((LongNum)divisor).value);
    }

    @Override
    public Num remainder(Num divisor) {
        return new LongNum(value%((LongNum)divisor).value);
    }

    @Override
    public Num pow(int n) {
        return new LongNum((long)Math.pow(value, n));
    }

    @Override
    public Num pow(Num n) {
        return new LongNum((long)Math.pow(value, ((LongNum)n).value));
    }

    @Override
    public Num sqrt() {
        return new LongNum((long)Math.sqrt(value));
    }

    @Override
    public Num sqrt(int precision) {
        return new LongNum((long)Math.sqrt(value));
    }

    @Override
    public Num abs() {
        return new LongNum(Math.abs(value));
    }

    @Override
    public boolean isZero() {
        return value==0;
    }

    @Override
    public boolean isPositive() {
        return value>0;
    }

    @Override
    public boolean isPositiveOrZero() {
        return value>=0;
    }

    @Override
    public boolean isNegative() {
        return value<0;
    }

    @Override
    public boolean isNegativeOrZero() {
        return value<=0;
    }

    @Override
    public boolean isEqual(Num other) {
        return value == ((LongNum)other).value;
    }

    @Override
    public boolean isGreaterThan(Num other) {
        return value > ((LongNum)other).value;
    }

    @Override
    public boolean isGreaterThanOrEqual(Num other) {
        return value >= ((LongNum)other).value;
    }

    @Override
    public boolean isLessThan(Num other) {
        return value < ((LongNum)other).value;
    }

    @Override
    public boolean isLessThanOrEqual(Num other) {
        return value <= ((LongNum)other).value;
    }

    @Override
    public Num min(Num other) {
        if ( value<=((LongNum)other).value) {
            return this;
        }
        return other;
    }

    @Override
    public Num max(Num other) {
        if ( value>=((LongNum)other).value) {
            return this;
        }
        return other;
    }

    @Override
    public Function<Number, Num> function() {
        return LongNum::valueOf;
    }

    public static Num valueOf(Number i) {
        return new LongNum(Long.parseLong(i.toString()));
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
