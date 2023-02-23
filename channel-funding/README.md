# Open Channel Interceptor plugin

This plugin provides an example of how to accept or reject `OpenChannel` messages received from remote peers based on a custom configuration file `channel_funding.conf` with parameters `open-channel-interceptor.min-active-channels` and `open-channel-interceptor.min-total-capacity`. It rejects `OpenChannel` requests from remote peers if they have less than the configured minimum public active channels or public total capacity.

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

