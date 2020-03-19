# payments
A payment processor for a Peergos storage server


# Testing
To run during testing use
> java -jar PaymentServer.jar -webroot assets -webcache false -peergos-address localhost:8000 -max-users 10 -stripe-secret $secret