# Tabconf Bolt12 tips

This awesome plugin lets you receive tips during Tabconf using Bolt12.

## Build

To build this plugin, run the following command in this directory:

```sh
mvn package
```

## Run

To run eclair with this plugin, start eclair with the following command:

```sh
eclair-node-<version>/bin/eclair-node.sh <path-to-plugin-jar>/tabconf-tips-plugin-<version>.jar
```

## Usage

This plugin lets you generate Bolt12 offers that can be used to receive tips for a great work done!

### API

This plugin adds APIs to `eclair` to manage Bolt12 offers.

#### Create a Bolt12 offer

```sh
$ eclair-cli createoffer --description="#reckless" --amountMsat=25000000

{
  "offerId": "0d97c0bfd3e0369aca824afeaf140fb3e89f77d3f972ba88dd5c05861c6cb0e1",
  "offer": "lno1qgsqvgnwgcg35z6ee2h3yczraddm72xrfua9uve2rlrm9deu7xyfzrcgqsqh67zqpgyjxun9vd4kcetnwvtzzqe92gvjwwcy98s96wu7zedra43nc79e0qu5xm5fxwxc2s53m7c0fy",
  "records": {
    "OfferChains": {
      "chains": [
        "06226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910f"
      ]
    },
    "OfferAmount": {
      "amount": 25000000
    },
    "OfferDescription": {
      "description": "#reckless"
    },
    "OfferNodeId": {
      "publicKey": "03255219273b0429e05d3b9e165a3ed633c78b97839436e89338d854291dfb0f49"
    }
  }
}
```

#### List Bolt12 offers

```sh
$ eclair-cli listoffers

[
  {
    "offerId": "0d97c0bfd3e0369aca824afeaf140fb3e89f77d3f972ba88dd5c05861c6cb0e1",
    "offer": "lno1qgsqvgnwgcg35z6ee2h3yczraddm72xrfua9uve2rlrm9deu7xyfzrcgqsqh67zqpgyjxun9vd4kcetnwvtzzqe92gvjwwcy98s96wu7zedra43nc79e0qu5xm5fxwxc2s53m7c0fy",
    "records": {
      "OfferChains": {
        "chains": [
          "06226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910f"
        ]
      },
      "OfferAmount": {
        "amount": 25000000
      },
      "OfferDescription": {
        "description": "#reckless"
      },
      "OfferNodeId": {
        "publicKey": "03255219273b0429e05d3b9e165a3ed633c78b97839436e89338d854291dfb0f49"
      }
    }
  },
  {
    "offerId": "1da2a9d9e2a72a84b6c078484c02b8c250aeb9ea25d5a18a2a2153c52e60c19d",
    "offer": "lno1qgsqvgnwgcg35z6ee2h3yczraddm72xrfua9uve2rlrm9deu7xyfzrc2q53hjmmvdutzzqe92gvjwwcy98s96wu7zedra43nc79e0qu5xm5fxwxc2s53m7c0fy",
    "records": {
      "OfferChains": {
        "chains": [
          "06226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910f"
        ]
      },
      "OfferDescription": {
        "description": "#yolo"
      },
      "OfferNodeId": {
        "publicKey": "03255219273b0429e05d3b9e165a3ed633c78b97839436e89338d854291dfb0f49"
      }
    }
  }
]
```

#### List payments received

```sh
$ eclair-cli listreceivedofferpayments

[
  {
    "amount": 15000,
    "paymentHash": "be7bcf14736a3b409cf9592b4e43cd9965a62b04ee4aa4714845c19ee5525d34",
    "description": "#reckless"
  }
]
```

### Database

This plugin stores the offers it generates in a `tabconf-tips.sqlite` database.
