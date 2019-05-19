package trader.service.ta;

import java.util.function.Function;

import org.ta4j.core.num.Num;

import trader.common.util.PriceUtil;

/**
 * long表示价格, 4位小数
 */
public class LongNum implements Num {
    private static final long serialVersionUID = -6389115676116240242L;

    public static final LongNum ZERO = new LongNum(0);

    private long value;

    public LongNum(double number) {
        this.value = PriceUtil.price2long(number);
    }

    public LongNum(long rawValue) {
        this.value = rawValue;
    }

    @Override
    public int compareTo(Num o) {
        return Long.compare(value, ((LongNum)o).value);
    }

    @Override
    public Number getDelegate() {
        return PriceUtil.long2price(value);
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
        double v = PriceUtil.long2price(value);
        double v2 = PriceUtil.long2price(((LongNum)multiplicand).value);
        double r = v*v2;
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num dividedBy(Num divisor) {
        if ( divisor.isZero() ) {
            return new LongNum(Long.MAX_VALUE);
        }
        double v = PriceUtil.long2price(value);
        double v2 = PriceUtil.long2price(((LongNum)divisor).value);
        double r = v/v2;
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num remainder(Num divisor) {
        double v = PriceUtil.long2price(value);
        double v2 = PriceUtil.long2price(((LongNum)divisor).value);
        double r = v%v2;
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num pow(int n) {
        double v = PriceUtil.long2price(value);
        double r = Math.pow(v, n);
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num pow(Num n) {
        double v = PriceUtil.long2price(value);
        double v2 = PriceUtil.long2price(((LongNum)n).value);
        double r = Math.pow(v, v2);
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num sqrt() {
        double v = PriceUtil.long2price(value);
        double r = Math.sqrt(v);
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num sqrt(int precision) {
        return sqrt();
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

    public long rawValue() {
        return value;
    }

    public static Num valueOf(Number i) {
        return new LongNum(PriceUtil.price2long(i.doubleValue()));
    }

    public static LongNum fromNum(Num num) {
        if ( num instanceof LongNum ) {
            return (LongNum)num;
        }else {
            return new LongNum(PriceUtil.double2price(num.doubleValue()));
        }
    }

    @Override
    public String toString() {
        return PriceUtil.long2str(value);
    }
}
