# Creating plugins for eclair

Eclair supports plugins written in Scala, Java, or any JVM-compatible language.

A valid plugin is a jar that contains an implementation of the [Plugin](https://github.com/ACINQ/eclair/blob/master/eclair-node/src/main/scala/fr/acinq/eclair/Plugin.scala) interface, and a manifest entry for `Main-Class` with the FQDN of the implementation.

Follow the instructions below to add a new plugin.

## Create plugin module

First of all, create a directory for the plugin at the root of the repository.
Add a `README.md` file explaining what the plugin does and how to use it.

Add a `pom.xml`, using the following template:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>fr.acinq.eclair</groupId>
        <artifactId>eclair-plugins_2.13</artifactId>
        <!-- Use the current version found in the root pom.xml -->
        <version>x.x.x</version>
    </parent>

    <!-- Replace with the name of your plugin -->
    <artifactId>my-fancy-plugin</artifactId>
    <packaging>jar</packaging>
    <name>my-fancy-plugin</name>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.2.1</version>
                <configuration>
                    <transformers>
                        <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                            <manifestEntries>
                                <!-- Replace with your main class (added in the next steps) -->
                                <Main-Class>fr.acinq.eclair.plugins.myfancyplugin.MyFancyPlugin</Main-Class>
                            </manifestEntries>
                        </transformer>
                    </transformers>
                </configuration>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <dependency>
            <groupId>org.scala-lang</groupId>
            <artifactId>scala-library</artifactId>
            <version>${scala.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>fr.acinq.eclair</groupId>
            <artifactId>eclair-core_${scala.version.short}</artifactId>
            <version>${project.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>fr.acinq.eclair</groupId>
            <artifactId>eclair-node_${scala.version.short}</artifactId>
            <version>${project.version}</version>
            <scope>provided</scope>
        </dependency>
        <!-- TESTS -->
        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-testkit_${scala.version.short}</artifactId>
            <version>${akka.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-actor-testkit-typed_${scala.version.short}</artifactId>
            <version>${akka.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>fr.acinq.eclair</groupId>
            <artifactId>eclair-core_${scala.version.short}</artifactId>
            <version>${project.version}</version>
            <classifier>tests</classifier>
            <type>test-jar</type>
            <scope>test</scope>
        </dependency>
    </dependencies>

</project>
```

Add your plugin to the root `pom.xml`'s module list:

```xml
<modules>
    <module>already-existing-plugin</module>
    <!-- Replace with the name of your plugin -->
    <module>my-fancy-plugin</module>
</modules>
```

## Add plugin implementation

It's now time to actually write some code!
In the plugin directory, create directories for your code and your tests:

- `src/main/scala/fr/acinq/eclair/plugins/myfancyplugin`
- `src/test/scala/fr/acinq/eclair/plugins/myfancyplugin`

In the code directory, create a `MyFancyPlugin.scala` file and implement the [Plugin](https://github.com/ACINQ/eclair/blob/master/eclair-node/src/main/scala/fr/acinq/eclair/Plugin.scala) interface:

```scala
package fr.acinq.eclair.plugins.myfancyplugin

import fr.acinq.eclair.{Kit, NodeParams, Plugin, PluginParams, Setup}
import grizzled.slf4j.Logging

class MyFancyPlugin extends Plugin with Logging {

  override def params: PluginParams = new PluginParams {
    override def name: String = "MyFancyPlugin"
  }

  override def onSetup(setup: Setup): Unit = {
    // This is where you can run some setup code, such as creating/connecting to a database.
  }

  override def onKit(kit: Kit): Unit = {
    // The kit provides access to eclair's actor system: you may start new actors and interact with existing actors here.
  }

}
```

You can then add the logic for your plugin.
Don't forget to add a thorough test suite: that will ensure your plugin will detect when changes to `eclair` require updates to the plugin logic.

## Guides

To help you implement common tasks, we have created some guides:

- [Persisting plugin data](./Persistence.md)
- [Adding APIs to interact with plugins](./Api.md)
