# Open Channel Interceptor plugin

This plugin provides an example of how to accept or reject `OpenChannel` messages received from remote peers based on a custom configuration file `channel_funding.conf` with parameters and defaults described in `reference.conf`.

It rejects `OpenChannel` requests from public remote peers if they have less than the configured minimum active channels or total capacity.

All `OpenChannel` requests from private remote peers are rejected unless `allow-private-nodes=true`.

Disclaimer: this plugin is for demonstration purposes only.

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

