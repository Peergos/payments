package peergos.payment;

import peergos.payment.util.*;

public interface Pricer {

    Natural convertBytesToCents(Natural bytes);

    Natural priceDifferenceInCents(Natural currentQuotaBytes, Natural newQuotaBytes);
}
