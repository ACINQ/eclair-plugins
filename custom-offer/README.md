# Custom offer example

Eclair supports basic offers out of the box, that's what you should use in most cases.
If you need more customization, this plugin is an example of how to do it.

## Config

```
features.option_onion_messages=optional
features.option_route_blinding=optional

custom-offer {
  max-final-expiry-delta = 1000
  avoid-nodes = [
    "020202020202020202020202020202020202020202020202020202020202020202",
    "030303030303030303030303030303030303030303030303030303030303030303"
  ]
}
```

## API

`customoffer --amount=1000 --description=test --introductionnode=022222222222222222222222222222222222222222222222222222222222222222` will generate and print a new offer