package peergos.payment;

import java.time.*;

public class IntentResult {

    public final String id;
    public final String clientSecret;
    public final LocalDateTime created;

    public IntentResult(String id, String clientSecret, LocalDateTime created) {
        this.id = id;
        this.clientSecret = clientSecret;
        this.created = created;
    }
}
