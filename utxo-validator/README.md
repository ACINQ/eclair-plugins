# UTXO validator plugin

This plugin provides an example of how to validate channels or splice transactions that contain UTXOs provided by an untrusted remote node.
This can only be done after the transaction has been created and published, because in some cases only the remote node has access to the fully signed transaction.
It is then possible to immediately close the channel, before its funding transaction has enough confirmations, to prevent it from being used.

Disclaimer: this plugin is for demonstration purposes only, node operators should fork this plugin and implement whatever policies make sense for their node.

## Build

To build this plugin, run the following command in this directory:

```sh
mvn package
```

## Run

To run eclair with this plugin, start eclair with the following command:

```sh
eclair-node-<version>/bin/eclair-node.sh <path-to-plugin-jar>/utxo-validator-<version>.jar
```
