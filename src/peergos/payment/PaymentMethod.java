package peergos.payment;

public class PaymentMethod {

    public final String id;
    public final String json;

    public PaymentMethod(String id, String json) {
        this.id = id;
        this.json = json;
    }
}
