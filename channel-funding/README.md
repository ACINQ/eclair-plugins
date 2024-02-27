# Channel Funding plugin

This plugin provides an example of how to accept or reject `open_channel` messages received from remote peers based on a custom configuration file `channel_funding.conf`.
Documentation for the various configuration options can be found in the [default configuration file](/src/main/resources/reference.conf).

Disclaimer: this plugin is for demonstration purposes only, node operators should fork this plugin and implement whatever policies make sense for their node. 

## Build

To build this plugin, run the following command in this directory:

```sh
mvn package
```

## Run

To run eclair with this plugin, start eclair with the following command:

```sh
eclair-node-<version>/bin/eclair-node.sh <path-to-plugin-jar>/channel-funding-plugin-<version>.jar
```
