# Bolt12 Tip Jar

Plugin that creates a basic offer to receive tips.

## Config

```
features.option_onion_messages=optional
features.option_route_blinding=optional

tip-jar {
  description = "donation to eclair"
  default-amount-msat = 100000000 // Amount to use if the invoice request does not specify an amount
  max-final-expiry-delta = 1000 // How long (in blocks) the route to pay the invoice will be valid
  // To hide our node (optional): 
  intermediate-nodes = [
    "020202020202020202020202020202020202020202020202020202020202020202",
    "030303030303030303030303030303030303030303030303030303030303030303"
  ]
  dummy-hops = 3
}
```

## API

`tipjarshowoffer` will print the tip jar offer.