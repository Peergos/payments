package peergos.payment.util;

import java.util.Objects;

public class Natural implements Comparable<Natural> {

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
    public int compareTo(Natural y) {
        return Long.compare(val, y.val);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Natural natural = (Natural) o;
        return val == natural.val;
    }

    @Override
    public int hashCode() {
        return Objects.hash(val);
    }

    @Override
    public String toString() {
        return Long.toString(val);
    }
}
