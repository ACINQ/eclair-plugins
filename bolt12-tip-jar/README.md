# Bolt12 Tip Jar

Plugin that creates a basic offer to receive tips.

## Config

```
tip-jar {
  description = "donation to eclair"
  default-amount-msat = 100000000 // Amount to use if the invoice request does not specify an amount
  max-final-expiry-delta = 1000 // How long (in blocks) the route to pay the invoice will be valid
}
```

## API

`tipjarshowoffer` will print the tip jar offer.