package peergos.payments;

import peergos.payments.util.*;

public interface Converter {

    Natural convertBytesToCents(Natural bytes);

    Natural convertCentsToBytes(Natural cents);
}
