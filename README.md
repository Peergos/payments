# payments
A payment processor for a Peergos storage server

To run use
> java -jar PaymentServer.jar -peergos-address localhost:8000 -max-users 10 -stripe-secret $secret

# Testing
To run during testing use
> java -jar PaymentServer.jar -webroot assets -webcache false -peergos-address localhost:8000 -max-users 10 -stripe-secret $secret

There are test card numbers here:

https://stripe.com/docs/testing

# Basic flow
The Stripe documentation for this is here: https://stripe.com/docs/payments/save-and-reuse

1. At sign up

   Payment server: createCustomer() => customer token
2. When user clicks add card button call

   getPaymentProperties(newSecret=true) on peergos server, which proxies to payment server
   
   Payment server: => createIntentToAddCard(customer token) => customer secret
3. User fills out card details and submits to stripe with customer secret

## Taking a payment (All on Peergos server)
1. getPaymentMethods => List<payment method>
2. select most recent payment method
3. take payment