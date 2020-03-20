package peergos.payment;

public class CardToken {
    public final String token;

    public CardToken(String token) {
        this.token = token;
    }

    public String toString() {
        return token;
    }
}
