# Eclair Plugins

[![Build Status](https://github.com/acinq/eclair-plugins/workflows/Build%20&%20Test/badge.svg)](https://github.com/acinq/eclair-plugins/actions?query=workflow%3A%22Build+%26+Test%22)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

This repository contains plugins for [eclair](https://github.com/acinq/eclair) that are officially maintained by ACINQ.

## Running eclair with plugins

Here is how to run eclair with plugins:

```shell
eclair-node-<version>/bin/eclair-node.sh <plugin1.jar> <plugin2.jar> <...>
```

## Creating a plugin

To create a new plugin, follow the instructions in our [documentation](./docs/README.md).

## Unofficial plugins

There are also plugins provided by external contributors from the eclair community.
We provide a non-exhaustive list of these plugins below.
If you need support for these plugins, head over to their respective github repository.

* [Telegram bot for eclair alerts](https://github.com/engenegr/eclair-alarmbot-plugin)
* [Hosted Channels](https://github.com/btcontract/plugin-hosted-channels)
* [Nostr Alarm Bot](https://github.com/rorp/eclair-nostr-bot)
* [Dynamic fees eclair plugin](https://github.com/rorp/eclair-plugin-dynamicfees)
