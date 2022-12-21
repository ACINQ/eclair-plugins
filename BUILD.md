# Building eclair plugins

## Requirements

- [OpenJDK 11](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot).
- [Maven](https://maven.apache.org/download.cgi) 3.6.3 or newer

## Build

Eclair plugins are packaged as jar files that contain an implementation of eclair's [Plugin](https://github.com/ACINQ/eclair/blob/master/eclair-node/src/main/scala/fr/acinq/eclair/Plugin.scala) interface.

To build all plugins and run the tests, simply run:

```shell
mvn package
```

Notes:

- If the build fails, you may need to clean previously built artifacts with the `mvn clean` command.
- Packaged plugins can be found in the `target` folder for each plugin.

### Skip tests

Running tests takes time. If you want to skip them, use `-DskipTests`:

```shell
mvn package -DskipTests
```

### Run tests

To only run the tests, run:

```shell
mvn test
```

To run tests for a specific class, run:

```shell
mvn test -Dsuites=*<TestClassName>
```

### Build specific plugin

To only build a specific plugin, run:

```shell
mvn package -pl <plugin-name> -am -Dmaven.test.skip=true
```
