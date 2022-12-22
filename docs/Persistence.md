# Persisting plugin data

Plugins may need to persist some data to provide useful features across node restarts.
We recommend creating a [SQLite](https://www.sqlite.org/index.html) database for that: eclair provide utilities to help you with that.

## Connecting to your database

In your plugin's main class, you can create or connect to your database during the `onSetup` phase:

```scala
package fr.acinq.eclair.plugins.myfancyplugin

import fr.acinq.eclair.db.sqlite.SqliteUtils
import fr.acinq.eclair.{Kit, NodeParams, Plugin, Setup}
import grizzled.slf4j.Logging

import java.io.File

class MyFancyPlugin extends Plugin with Logging {

  var db: MyFancyPluginDb = _

  override def onSetup(setup: Setup): Unit = {
    // We create our DB in the per-chain data directory.
    val chain = setup.config.getString("chain")
    val chainDir = new File(setup.datadir, chain)
    db = new SqliteMyFancyPluginDb(SqliteUtils.openSqliteFile(chainDir, "my-fancy-plugin.sqlite", exclusiveLock = true, journalMode = "wal", syncFlag = "normal"))
  }

}
```

## Implementing database operations

You'll need to implement the database operations necessary for your plugin.
We recommend versioning your data, which makes it easy to update your data model and migrate old data to guarantee backwards-compatibility.

```scala
trait MyFancyPluginDb {
  def addFancyStuff(fancyId: ByteVector32, fancyData: ByteVector): Unit
}

object SqliteMyFancyPluginDb {
  val CURRENT_VERSION = 1
  val DB_NAME = "my_fancy_plugin"
}

class SqliteMyFancyPluginDb(sqlite: Connection) extends MyFancyPluginDb {

  import SqliteMyFancyPluginDb._
  import fr.acinq.eclair.db.jdbc.JdbcUtils.ExtendedResultSet._
  import fr.acinq.eclair.db.sqlite.SqliteUtils._

  using(sqlite.createStatement(), inTransaction = true) { statement =>
    getVersion(statement, DB_NAME) match {
      case None =>
        statement.executeUpdate("CREATE TABLE my_fancy_table (fancy_id TEXT NOT NULL PRIMARY KEY, fancy_data BLOB NOT NULL, created_timestamp INTEGER NOT NULL)")
        statement.executeUpdate("CREATE INDEX created_timestamp_idx ON my_fancy_table(created_timestamp)")
      case Some(CURRENT_VERSION) => () // table is up-to-date, nothing to do
      case Some(unknownVersion) => throw new RuntimeException(s"Unknown version of DB $DB_NAME found, version=$unknownVersion")
    }
    setVersion(statement, DB_NAME, CURRENT_VERSION)
  }

  override def addFancyStuff(fancyId: ByteVector32, fancyData: ByteVector): Unit = {
    using(sqlite.prepareStatement("INSERT INTO my_fancy_table (fancy_id, fancy_data, created_timestamp) VALUES (?, ?, ?)")) { statement =>
      statement.setString(1, fancyId.toHex)
      statement.setBytes(2, fancyData.toByteArray())
      statement.setLong(3, TimestampSecond.now().toLong)
      statement.executeUpdate()
    }
  }

}
```

## Updating database version

If you need to make a change to your data model, you should add a migration of the old data to guarantee backwards-compatibility.
Let's add a new column to `my_fancy_table` for example:

```scala
object SqliteMyFancyPluginDb {
  val CURRENT_VERSION = 2
  val DB_NAME = "my_fancy_plugin"
}

class SqliteMyFancyPluginDb(sqlite: Connection) extends MyFancyPluginDb with Logging {

  import SqliteMyFancyPluginDb._
  import fr.acinq.eclair.db.jdbc.JdbcUtils.ExtendedResultSet._
  import fr.acinq.eclair.db.sqlite.SqliteUtils._

  using(sqlite.createStatement(), inTransaction = true) { statement =>

    def migration12(statement: Statement): Unit = {
      statement.executeUpdate("ALTER TABLE my_fancy_table ADD COLUMN more_fancy_data BLOB NULL")
    }

    getVersion(statement, DB_NAME) match {
      case None =>
        statement.executeUpdate("CREATE TABLE my_fancy_table (fancy_id TEXT NOT NULL PRIMARY KEY, fancy_data BLOB NOT NULL, more_fancy_data BLOB, created_timestamp INTEGER NOT NULL)")
        statement.executeUpdate("CREATE INDEX created_timestamp_idx ON my_fancy_table(created_timestamp)")
      case Some(v@1) =>
        logger.warn(s"migrating db $DB_NAME, found version=$v current=$CURRENT_VERSION")
        migration12(statement)
      case Some(CURRENT_VERSION) => () // table is up-to-date, nothing to do
      case Some(unknownVersion) => throw new RuntimeException(s"Unknown version of DB $DB_NAME found, version=$unknownVersion")
    }
    setVersion(statement, DB_NAME, CURRENT_VERSION)
  }

  override def addFancyStuff(fancyId: ByteVector32, fancyData: ByteVector): Unit = ???

}
```
