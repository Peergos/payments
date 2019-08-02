package peergos.payment;

import peergos.payment.util.*;

public interface Converter {

    Natural convertBytesToCents(Natural bytes);

    Natural convertCentsToBytes(Natural cents);
}
