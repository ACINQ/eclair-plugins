# Building eclair plugins

## Requirements

- [OpenJDK 11](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot)
- [Maven](https://maven.apache.org/download.cgi) 3.6.3 or newer

## Build eclair

Eclair plugins depend on eclair: you must first have a packaged version of eclair in your local maven repository (usually found in `$HOME/.m2`).
Clone the version of eclair you're interested in (in most cases it will either be `master` or the latest release) and, in the eclair repository, run the following commands:

```shell
# Install eclair to your local maven repository:
mvn install -DskipTests
# Get the corresponding eclair version:
ECLAIR_VERSION=$(mvn help:evaluate -q -Dexpression=project.version -DforceStdout)
```

## Build

Eclair plugins are packaged as jar files that contain an implementation of eclair's [Plugin](https://github.com/ACINQ/eclair/blob/master/eclair-node/src/main/scala/fr/acinq/eclair/Plugin.scala) interface.

To build all plugins and run the tests, simply run:

```shell
mvn package
```

If you're using a version of eclair that is different from the version of eclair-plugins, you'll need to specify it explicitly:

```shell
mvn -Declair.version=$ECLAIR_VERSION package 
```

You can also change the `eclair.version` property in `pom.xml`.

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

To run tests using a specific number of threads, run:

```shell
mvn -T <thread_count> test
```

### Build specific plugins

To only build a specific plugin, run the previous commands directly in its directory:

```shell
cd plugin-name
mvn package
```
