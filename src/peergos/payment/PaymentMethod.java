package peergos.payment;

public class PaymentMethod {

    public final String id;
    public final long created; // epoch seconds

    public PaymentMethod(String id, long created) {
        this.id = id;
        this.created = created;
    }
}
