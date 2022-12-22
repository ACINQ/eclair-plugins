# Historical Gossip

This plugins records every gossip message we receive in an append-only sqlite database.
This can be useful for research purposes and analysis of the evolution of the network graph.
We don't recommend running this plugin on a routing node though.

## Build

To build this plugin, run the following command in this directory:

```sh
mvn package
```

## Run

To run eclair with this plugin, start eclair with the following command:

```sh
eclair-node-<version>/bin/eclair-node.sh <path-to-plugin-jar>/historical-gossip-plugin-<version>.jar
```

## Database

This plugin stores gossip data in a `historical-gossip.sqlite` database.
This database is located in your eclair data directory, in the directory of the chain you're using (`regtest`, `testnet` or `mainnet`).
