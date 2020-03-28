package peergos.payment;

import peergos.payment.util.*;

public class LinearPricer implements Pricer {

    private final Natural bytesPerCent;

    public LinearPricer(Natural bytesPerCent) {
        this.bytesPerCent = bytesPerCent;
    }

    @Override
    public Natural convertBytesToCents(Natural bytes) {
        return bytes.divide(bytesPerCent);
    }

    @Override
    public Natural priceDifferenceInCents(Natural currentQuotaBytes, Natural newQuotaBytes) {
        return convertBytesToCents(newQuotaBytes).minus(convertBytesToCents(currentQuotaBytes));
    }
}
