package peergos.payment.tests;

import org.junit.*;
import peergos.payment.*;
import peergos.payment.util.*;

import java.time.*;
import java.util.*;

public class PaymentStateTests {

    private static final long GIGABYTE = 1024*1024*1024L;
    private static final String cardtoken = "tok_1F2lzWKU7V27QSzndZCle14H";

    private static class AcceptAll implements Bank {
        private final List<PaymentResult> payments = new ArrayList<>();
        private int failuresLeft = 0;
        private String errorMessage = "Failed payment";

        public List<PaymentResult> getPayments() {
            return new ArrayList<>(payments);
        }

        public synchronized void failNext(String error) {
            failuresLeft = 1;
            errorMessage = error;
        }

        @Override
        public synchronized PaymentResult takePayment(CardToken cardToken, Natural cents, String currency, LocalDateTime now) {
            PaymentResult res;
            if (failuresLeft > 0) {
                res = new PaymentResult(cents, currency, now, Optional.of(errorMessage));
                failuresLeft--;
            } else {
                res = new PaymentResult(cents, currency, now, Optional.empty());
            }
            payments.add(res);
            return res;
        }
    }

    @Test
    public void paymentStateEvolution() {
        Natural bytesPerCent = new Natural(GIGABYTE / 100);
        Natural minQuota = new Natural(5 * GIGABYTE);
        PaymentState global = new PaymentState(new HashMap<>(), bytesPerCent, minQuota, new AcceptAll());
        String username = "bob";
        CardToken card = new CardToken(cardtoken);
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        global.ensureUser(username);
        LocalDateTime now = LocalDateTime.now();
        global.setDesiredQuota(username, desiredQuota, now);
        global.addCard(username, card, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val);
    }

    @Test
    public void idempotent() {
        Natural bytesPerCent = new Natural(GIGABYTE / 100);
        Natural minQuota = new Natural(5 * GIGABYTE);
        AcceptAll bank = new AcceptAll();
        PaymentState global = new PaymentState(new HashMap<>(), bytesPerCent, minQuota, bank);
        String username = "bob";
        CardToken card = new CardToken(cardtoken);
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        global.ensureUser(username);
        LocalDateTime now = LocalDateTime.now();
        global.setDesiredQuota(username, desiredQuota, now);
        global.addCard(username, card, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val);

        for (int i=0; i < 10; i++)
            global.processAll(now);

        List<PaymentResult> payments = bank.getPayments();
        Assert.assertTrue("One payment", payments.size() == 1);
    }

    @Test
    public void freeLevel() {
        Natural bytesPerCent = new Natural(GIGABYTE / 100);
        Natural minQuota = new Natural(5 * GIGABYTE);
        AcceptAll bank = new AcceptAll();
        HashMap<String, PaymentState.UserState> userStates = new HashMap<>();
        PaymentState global = new PaymentState(userStates, bytesPerCent, minQuota, bank);
        String username = "bob";
        CardToken card = new CardToken(cardtoken);
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        Natural freeSpace = new Natural(5 * GIGABYTE);
        global.ensureUser(username, freeSpace);
        LocalDateTime now = LocalDateTime.now();
        global.setDesiredQuota(username, desiredQuota, now);
        global.addCard(username, card, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.plus(freeSpace).val);

        List<PaymentResult> payments = bank.getPayments();
        Assert.assertTrue("One payment", payments.size() == 1);
        Assert.assertTrue("One payment", payments.get(0).amount.val == 500);
    }

    @Test
    public void chargeOnExpiry() {
        Natural bytesPerCent = new Natural(GIGABYTE / 100);
        Natural minQuota = new Natural(5 * GIGABYTE);
        AcceptAll bank = new AcceptAll();
        PaymentState global = new PaymentState(new HashMap<>(), bytesPerCent, minQuota, bank);
        String username = "bob";
        CardToken card = new CardToken(cardtoken);
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        global.ensureUser(username);
        LocalDateTime now = LocalDateTime.now();
        global.setDesiredQuota(username, desiredQuota, now);
        global.addCard(username, card, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val);

        for (int i=0; i < 28; i++)
            global.processAll(now.plusDays(i));

        global.processAll(now.plusMonths(1).plusDays(1));

        List<PaymentResult> payments = bank.getPayments();
        Assert.assertTrue("Two payments", payments.size() == 2);
    }

    @Test
    public void failAndRecover() {
        Natural bytesPerCent = new Natural(GIGABYTE / 100);
        Natural minQuota = new Natural(5 * GIGABYTE);
        AcceptAll bank = new AcceptAll();
        PaymentState global = new PaymentState(new HashMap<>(), bytesPerCent, minQuota, bank);
        String username = "bob";
        CardToken card = new CardToken(cardtoken);
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        global.ensureUser(username);
        LocalDateTime now = LocalDateTime.now();
        global.setDesiredQuota(username, desiredQuota, now);
        global.addCard(username, card, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val);

        bank.failNext("Card Rejected!");
        global.processAll(now.plusMonths(1).plusDays(1));
        {
            List<PaymentResult> payments = bank.getPayments();
            Assert.assertTrue("Two payments", payments.size() == 2);
            Assert.assertTrue("Last failed", ! payments.get(1).isSuccessful());
        }

        global.processAll(now.plusMonths(1).plusDays(2));
        {
            List<PaymentResult> payments = bank.getPayments();
            Assert.assertTrue("Three payments", payments.size() == 3);
            Assert.assertTrue("Last succeeded", payments.get(2).isSuccessful());
        }
    }

    @Test
    public void increaseQuotaAndTakePayment() {
        Natural bytesPerCent = new Natural(GIGABYTE / 100);
        Natural minQuota = new Natural(5 * GIGABYTE);
        PaymentState global = new PaymentState(new HashMap<>(), bytesPerCent, minQuota, new AcceptAll());
        String username = "bob";
        CardToken card = new CardToken(cardtoken);
        Natural desiredQuota = new Natural(5 * GIGABYTE);

        global.ensureUser(username);
        LocalDateTime now = LocalDateTime.now();
        global.setDesiredQuota(username, desiredQuota, now);
        global.addCard(username, card, now);
        long quota = global.getCurrentQuota(username);


        Assert.assertTrue("Correct quota", quota == desiredQuota.val);
        Natural increasedQuota = new Natural(10 * GIGABYTE);
        global.setDesiredQuota(username, increasedQuota, now);
        long newQuota = global.getCurrentQuota(username);
        Assert.assertTrue("Quota increased", newQuota == increasedQuota.val);
    }

    private static final String example_payment_response = "{\n" +
            "  \"id\": \"ch_1F2lzpKU7V27QSznGqy1VLhY\",\n" +
            "  \"object\": \"charge\",\n" +
            "  \"amount\": 500,\n" +
            "  \"amount_refunded\": 0,\n" +
            "  \"application\": null,\n" +
            "  \"application_fee\": null,\n" +
            "  \"application_fee_amount\": null,\n" +
            "  \"balance_transaction\": \"txn_1F2lzpKU7V27QSznphoEUF9q\",\n" +
            "  \"billing_details\": {\n" +
            "    \"address\": {\n" +
            "      \"city\": null,\n" +
            "      \"country\": null,\n" +
            "      \"line1\": null,\n" +
            "      \"line2\": null,\n" +
            "      \"postal_code\": null,\n" +
            "      \"state\": null\n" +
            "    },\n" +
            "    \"email\": null,\n" +
            "    \"name\": null,\n" +
            "    \"phone\": null\n" +
            "  },\n" +
            "  \"captured\": true,\n" +
            "  \"created\": 1564695577,\n" +
            "  \"currency\": \"gbp\",\n" +
            "  \"customer\": null,\n" +
            "  \"description\": null,\n" +
            "  \"destination\": null,\n" +
            "  \"dispute\": null,\n" +
            "  \"failure_code\": null,\n" +
            "  \"failure_message\": null,\n" +
            "  \"fraud_details\": {\n" +
            "  },\n" +
            "  \"invoice\": null,\n" +
            "  \"livemode\": false,\n" +
            "  \"metadata\": {\n" +
            "  },\n" +
            "  \"on_behalf_of\": null,\n" +
            "  \"order\": null,\n" +
            "  \"outcome\": {\n" +
            "    \"network_status\": \"approved_by_network\",\n" +
            "    \"reason\": null,\n" +
            "    \"risk_level\": \"normal\",\n" +
            "    \"risk_score\": 40,\n" +
            "    \"seller_message\": \"Payment complete.\",\n" +
            "    \"type\": \"authorized\"\n" +
            "  },\n" +
            "  \"paid\": true,\n" +
            "  \"payment_intent\": null,\n" +
            "  \"payment_method\": \"card_1F2lzWKU7V27QSzn1GoHte5d\",\n" +
            "  \"payment_method_details\": {\n" +
            "    \"card\": {\n" +
            "      \"brand\": \"visa\",\n" +
            "      \"checks\": {\n" +
            "        \"address_line1_check\": null,\n" +
            "        \"address_postal_code_check\": null,\n" +
            "        \"cvc_check\": \"pass\"\n" +
            "      },\n" +
            "      \"country\": \"US\",\n" +
            "      \"exp_month\": 11,\n" +
            "      \"exp_year\": 2021,\n" +
            "      \"fingerprint\": \"aL93V1qBverYGEKx\",\n" +
            "      \"funding\": \"credit\",\n" +
            "      \"last4\": \"4242\",\n" +
            "      \"three_d_secure\": null,\n" +
            "      \"wallet\": null\n" +
            "    },\n" +
            "    \"type\": \"card\"\n" +
            "  },\n" +
            "  \"receipt_email\": null,\n" +
            "  \"receipt_number\": null,\n" +
            "  \"receipt_url\": \"https://pay.stripe.com/receipts/acct_174caQKU7V27QSzn/ch_1F2lzpKU7V27QSznGqy1VLhY/rcpt_FXrjkNxvWIaDIt8FXW63ltDmsVvGI5P\",\n" +
            "  \"refunded\": false,\n" +
            "  \"refunds\": {\n" +
            "    \"object\": \"list\",\n" +
            "    \"data\": [\n" +
            "\n" +
            "    ],\n" +
            "    \"has_more\": false,\n" +
            "    \"total_count\": 0,\n" +
            "    \"url\": \"/v1/charges/ch_1F2lzpKU7V27QSznGqy1VLhY/refunds\"\n" +
            "  },\n" +
            "  \"review\": null,\n" +
            "  \"shipping\": null,\n" +
            "  \"source\": {\n" +
            "    \"id\": \"card_1F2lzWKU7V27QSzn1GoHte5d\",\n" +
            "    \"object\": \"card\",\n" +
            "    \"address_city\": null,\n" +
            "    \"address_country\": null,\n" +
            "    \"address_line1\": null,\n" +
            "    \"address_line1_check\": null,\n" +
            "    \"address_line2\": null,\n" +
            "    \"address_state\": null,\n" +
            "    \"address_zip\": null,\n" +
            "    \"address_zip_check\": null,\n" +
            "    \"brand\": \"Visa\",\n" +
            "    \"country\": \"US\",\n" +
            "    \"customer\": null,\n" +
            "    \"cvc_check\": \"pass\",\n" +
            "    \"dynamic_last4\": null,\n" +
            "    \"exp_month\": 11,\n" +
            "    \"exp_year\": 2021,\n" +
            "    \"fingerprint\": \"aL93V1qBverYGEKx\",\n" +
            "    \"funding\": \"credit\",\n" +
            "    \"last4\": \"4242\",\n" +
            "    \"metadata\": {\n" +
            "    },\n" +
            "    \"name\": null,\n" +
            "    \"tokenization_method\": null\n" +
            "  },\n" +
            "  \"source_transfer\": null,\n" +
            "  \"statement_descriptor\": null,\n" +
            "  \"status\": \"succeeded\",\n" +
            "  \"transfer_data\": null,\n" +
            "  \"transfer_group\": null\n" +
            "}";
}
