package peergos.payment;

import peergos.payment.util.*;

import java.util.*;

public class FixedPricer implements Pricer {

    private final Map<Natural, Natural> bytesToCents;

    public FixedPricer(Map<Natural, Natural> bytesToCents) {
        this.bytesToCents = bytesToCents;
    }

    @Override
    public Natural convertBytesToCents(Natural bytes) {
        return bytesToCents.get(bytes);
    }

    @Override
    public Natural priceDifferenceInCents(Natural currentQuotaBytes, Natural newQuotaBytes) {
        return bytesToCents.get(newQuotaBytes).minus(bytesToCents.get(currentQuotaBytes));
    }
}
