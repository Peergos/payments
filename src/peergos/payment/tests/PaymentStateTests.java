package peergos.payment.tests;

import org.junit.*;
import peergos.payment.*;
import peergos.payment.util.*;

import java.time.*;
import java.util.*;
import java.util.stream.*;

public class PaymentStateTests {

    private static final long MEGABYTE = 1024*1024L;
    private static final long GIGABYTE = 1024*1024*1024L;
    private static final long POUND = 100;
    private static final Natural freeQuota = new Natural(200 * 1024 * 1024);
    private static final Set<Natural> allowedQuotas = Stream.of(0L, 1*MEGABYTE, 5*GIGABYTE, 7*GIGABYTE, 10*GIGABYTE, 50*GIGABYTE)
            .map(Natural::of)
            .collect(Collectors.toSet());

    private static class AcceptAll implements Bank {
        private final Random r = new Random(0);
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

        private String rndString() {
            return Integer.toString(r.nextInt(Integer.MAX_VALUE));
        }

        @Override
        public CustomerResult createCustomer(String username) {
            String id = rndString();
            return new CustomerResult(id);
        }

        @Override
        public IntentResult setupIntent(CustomerResult cus) {
            return new IntentResult(rndString(), rndString(), LocalDateTime.now());
        }

        @Override
        public PaymentResult takePayment(CustomerResult cus,
                                         Natural cents,
                                         String currency,
                                         LocalDateTime now,
                                         Natural forQuota) {
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

    private static PaymentState buildPaymentState(Bank bank, Pricer pricer) {
        Natural minPayment = new Natural(500);
        return new PaymentState(new SqlPaymentStore(Builder.buildEphemeralSqlite(), false), pricer, minPayment, bank, freeQuota, 10, allowedQuotas);
    }

    private static PaymentState buildPaymentState(Bank bank) {
        Natural bytesPerCent = new Natural(GIGABYTE / 100);
        LinearPricer pricer = new LinearPricer(bytesPerCent);
        return buildPaymentState(bank, pricer);
    }

    @Test
    public void paymentStateEvolution() {
        PaymentState global = buildPaymentState(new AcceptAll());
        String username = "bob";
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        LocalDateTime now = LocalDateTime.now();
        global.ensureUser(username, now);
        global.setDesiredQuota(username, desiredQuota, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val + freeQuota.val);
    }

    @Test
    public void idempotent() {
        AcceptAll bank = new AcceptAll();
        PaymentState global = buildPaymentState(bank);
        String username = "bob";
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        LocalDateTime now = LocalDateTime.now();
        global.ensureUser(username, now);
        global.setDesiredQuota(username, desiredQuota, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val + freeQuota.val);

        for (int i=0; i < 10; i++)
            global.processAll(now);

        List<PaymentResult> payments = bank.getPayments();
        Assert.assertTrue("One payment", payments.size() == 1);
    }

    @Test
    public void freeLevel() {
        AcceptAll bank = new AcceptAll();
        PaymentState global = buildPaymentState(bank);
        String username = "bob";
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        Natural freeSpace = new Natural(5 * GIGABYTE);
        LocalDateTime now = LocalDateTime.now();
        global.ensureUser(username, freeSpace, now);
        global.setDesiredQuota(username, desiredQuota, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.plus(freeSpace).val);

        List<PaymentResult> payments = bank.getPayments();
        Assert.assertTrue("One payment", payments.size() == 1);
        Assert.assertTrue("One payment", payments.get(0).amount.val == 500);
    }

    @Test
    public void chargeOnExpiry() {
        AcceptAll bank = new AcceptAll();
        PaymentState global = buildPaymentState(bank);
        String username = "bob";
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        LocalDateTime now = LocalDateTime.now();
        global.ensureUser(username, now);
        global.setDesiredQuota(username, desiredQuota, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val + freeQuota.val);

        for (int i=0; i < 28; i++)
            global.processAll(now.plusDays(i));

        global.processAll(now.plusMonths(1).plusDays(1));

        List<PaymentResult> payments = bank.getPayments();
        Assert.assertTrue("Two payments", payments.size() == 2);
    }

    @Test
    public void failAndRecover() {
        AcceptAll bank = new AcceptAll();
        PaymentState global = buildPaymentState(bank);
        String username = "bob";
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        LocalDateTime now = LocalDateTime.now();
        global.ensureUser(username, now);
        global.setDesiredQuota(username, desiredQuota, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val + freeQuota.val);

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
        LinearPricer pricer = new LinearPricer(bytesPerCent);
        AcceptAll bank = new AcceptAll();
        PaymentState global = buildPaymentState(bank, pricer);
        String username = "bob";
        Natural desiredQuota = new Natural(5 * GIGABYTE);
        LocalDateTime now = LocalDateTime.now();
        global.ensureUser(username, now);
        global.setDesiredQuota(username, desiredQuota, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val + freeQuota.val);
        Natural increasedQuota = new Natural(10 * GIGABYTE);
        global.setDesiredQuota(username, increasedQuota, now);
        long newQuota = global.getCurrentQuota(username);
        Assert.assertTrue("Quota increased", newQuota == increasedQuota.val + freeQuota.val);
        List<PaymentResult> payments = bank.getPayments();
        Assert.assertTrue("Correct number of payments ", payments.size() == 2);
        Assert.assertTrue("First payment is for 5GiB", payments.get(0).amount.equals(pricer.convertBytesToCents(desiredQuota)));
        Assert.assertTrue("Second payment is for 5GiB",
                payments.get(1).amount.equals(pricer.convertBytesToCents(increasedQuota.minus(desiredQuota))));
    }

    @Test
    public void maintainExistingPricingAgreements() {
        Natural tenPounds = Natural.of(10 * POUND);
        Natural twentyPounds = Natural.of(20 * POUND);
        AcceptAll bank = new AcceptAll();
        SqlPaymentStore paymentState = new SqlPaymentStore(Builder.buildEphemeralSqlite(), false);
        String username = "bob";
        SortedMap<Natural, Natural> bytesToCents = new TreeMap<>();
        bytesToCents.put(Natural.ZERO, Natural.ZERO);
        bytesToCents.put(Natural.of(10 * GIGABYTE), tenPounds);
        bytesToCents.put(Natural.of(50 * GIGABYTE), Natural.of(30 * POUND));
        {
            FixedPricer pricer = new FixedPricer(bytesToCents);
            PaymentState global = new PaymentState(paymentState,
                    pricer, Natural.of(5 * POUND), bank, Natural.ZERO, 10, bytesToCents.keySet());
            Natural desiredQuota = new Natural(10 * GIGABYTE);
            LocalDateTime now = LocalDateTime.now();
            global.ensureUser(username, now);
            global.setDesiredQuota(username, desiredQuota, now);
            long quota = global.getCurrentQuota(username);
            global.processAll(now);
            Assert.assertTrue("Correct quota", quota == desiredQuota.val);
        }

        // Now lets simulate changing our pricing (for new users) but existing users should maintain old pricing
        bytesToCents.put(Natural.of(10 * GIGABYTE), twentyPounds);
        FixedPricer pricer = new FixedPricer(bytesToCents);
        PaymentState global = new PaymentState(paymentState,
                pricer, Natural.of(5 * POUND), bank, Natural.ZERO, 10, bytesToCents.keySet());
        global.processAll(LocalDateTime.now().plusMonths(1));
        List<PaymentResult> payments = bank.getPayments();
        Assert.assertTrue("Correct number of payments ", payments.size() == 2);
        Assert.assertTrue("First payment is for 10 pounds", payments.get(0).amount.equals(tenPounds));
        Assert.assertTrue("Second payment is same", payments.get(1).amount.equals(tenPounds));
    }

    @Test
    public void decreaseQuotaAndTakePayment() {
        Natural bytesPerCent = new Natural(GIGABYTE / 100);
        LinearPricer pricer = new LinearPricer(bytesPerCent);
        AcceptAll bank = new AcceptAll();
        PaymentState global = buildPaymentState(bank, pricer);
        String username = "bob";
        Natural desiredQuota = new Natural(10 * GIGABYTE);
        LocalDateTime now = LocalDateTime.now();
        global.ensureUser(username, now);
        global.setDesiredQuota(username, desiredQuota, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val + freeQuota.val);
        Natural decreasedQuota = new Natural(7 * GIGABYTE);
        global.setDesiredQuota(username, decreasedQuota, now);
        global.processAll(now.plusMonths(1));
        long newQuota = global.getCurrentQuota(username);
        Assert.assertTrue("Quota decreased", newQuota == decreasedQuota.val + freeQuota.val);
        List<PaymentResult> payments = bank.getPayments();
        Assert.assertTrue("Correct number of payments ", payments.size() == 2);
        Assert.assertTrue("First payment is for 10GiB", payments.get(0).amount.equals(pricer.convertBytesToCents(desiredQuota)));
        Assert.assertTrue("Second payment is for 7GiB", payments.get(1).amount.equals(pricer.convertBytesToCents(decreasedQuota)));

    }

    @Test
    public void deletedAccountIsNotChargedFurther() {
        Natural bytesPerCent = new Natural(GIGABYTE / 10);
        LinearPricer pricer = new LinearPricer(bytesPerCent);
        AcceptAll bank = new AcceptAll();
        PaymentState global = buildPaymentState(bank, pricer);
        String username = "bob";
        Natural desiredQuota = new Natural(50 * GIGABYTE);
        LocalDateTime now = LocalDateTime.now();
        global.ensureUser(username, now);
        global.setDesiredQuota(username, desiredQuota, now);
        long quota = global.getCurrentQuota(username);
        Assert.assertTrue("Correct quota", quota == desiredQuota.val + freeQuota.val);
        // simulate deleting account which sets quota to 1 MiB
        Natural decreasedQuota = new Natural(1 * MEGABYTE);
        global.setDesiredQuota(username, decreasedQuota, now);
        global.processAll(now.plusMonths(1));
        long newQuota = global.getCurrentQuota(username);
        Assert.assertTrue("Quota decreased", newQuota == decreasedQuota.val + freeQuota.val);
        List<PaymentResult> payments = bank.getPayments();
        Assert.assertTrue("Correct number of payments ", payments.size() == 1);
        Assert.assertTrue("First payment is correct", payments.get(0).amount.equals(pricer.convertBytesToCents(desiredQuota)));
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
