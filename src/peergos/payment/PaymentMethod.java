package peergos.payment;

public class PaymentMethod {

    public final String id;
    public final String email;
    public final long created; // epoch seconds

    public PaymentMethod(String id, String email, long created) {
        this.id = id;
        this.email = email;
        this.created = created;
    }
}
