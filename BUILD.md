# Building eclair plugins

## Requirements

- [OpenJDK 11](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot)
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

To run tests using a specific number of threads, run:

```shell
mvn -T <thread_count> test
```

### Build specific plugin

To only build a specific plugin, run the previous commands directly in its directory:

```shell
cd plugin-name
mvn package
```

### Build with a specific version of eclair

Sometimes, creating a new plugin will require some changes to eclair.
When that happens, you'll want to reference a locally modified version of eclair.

Once you've made changes to eclair, publish it to your local maven packages using the following command in the eclair repository:

```shell
mvn clean install -DskipTests
```

You will need to know the version used for that eclair package. In the eclair repository, run the following command:

```shell
ECLAIR_VERSION=$(mvn help:evaluate -q -Dexpression=project.version -DforceStdout)
```

You can then build the eclair plugins using this local eclair package by running the following command in the eclair-plugins repository:

```shell
mvn -Declair.version=$ECLAIR_VERSION package
```
