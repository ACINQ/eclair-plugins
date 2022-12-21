# Official Eclair Plugins

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

This repository contains plugins for [eclair](https://github.com/acinq/eclair) that are officially maintained by ACINQ.

## Running eclair with plugins

Here is how to run eclair with plugins:

```shell
eclair-node-<version>/bin/eclair-node.sh <plugin1.jar> <plugin2.jar> <...>
```

## Creating a plugin

Eclair supports plugins written in Scala, Java, or any JVM-compatible language.

A valid plugin is a jar that contains an implementation of the [Plugin](https://github.com/ACINQ/eclair/blob/master/eclair-node/src/main/scala/fr/acinq/eclair/Plugin.scala) interface, and a manifest entry for `Main-Class` with the FQDN of the implementation.

## Unofficial plugins

There are also plugins provided by external contributors from the eclair community.
We provide a non-exhaustive list of these plugins below.
If you need support for these plugins, head over to their respective github repository.

* [Telegram bot for eclair alerts](https://github.com/engenegr/eclair-alarmbot-plugin)
* [Hosted Channels](https://github.com/btcontract/plugin-hosted-channels)
