package peergos.payment;

public class CustomerResult {

    public final String id;
    public final String json;

    public CustomerResult(String id, String json) {
        this.id = id;
        this.json = json;
    }
}
