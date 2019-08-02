package peergos.payment.util;

public class Natural {

    public static final Natural ZERO = new Natural(0);

    public final long val;

    public Natural(long val) {
        if (val < 0)
            throw new IllegalStateException("Negative value: " + val);
        this.val = val;
    }

    public Natural minus(Natural other) {
        return new Natural(val - other.val);
    }

    public Natural plus(Natural other) {
        return new Natural(val + other.val);
    }

    public Natural times(Natural other) {
        return new Natural(val * other.val);
    }

    public Natural divide(Natural other) {
        return new Natural(val / other.val);
    }

    public Natural mod(Natural other) {
        return new Natural(val % other.val);
    }

    public Natural max(Natural other) {
        return val > other.val ? this : other;
    }

    public static Natural of(long val) {
        return new Natural(val);
    }

    @Override
    public String toString() {
        return Long.toString(val);
    }
}
