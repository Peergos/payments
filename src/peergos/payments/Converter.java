package peergos.payments;

public interface Converter {

    Natural convertBytesToCents(Natural bytes);

    Natural convertCentsToBytes(Natural cents);
}
