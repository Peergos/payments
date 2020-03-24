package peergos.payment;

public class PaymentMethod {

    public final String id;
    public final long created;
    public final String json;

    public PaymentMethod(String id, long created, String json) {
        this.id = id;
        this.created = created;
        this.json = json;
    }
}
