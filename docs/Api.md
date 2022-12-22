# Adding APIs to interact with plugins

Plugins may want to expose API endpoints for node operators.
Eclair provides utilities to add new APIs to the HTTP server exposed by `eclair-node`.
The HTTP server uses [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html).

## Registering API routes

In your plugin's main class, you can register API routes by extending the `RouteProvider` trait:

```scala
package fr.acinq.eclair.plugins.myfancyplugin

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, SupervisorStrategy}
import akka.http.scaladsl.server.Route
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.{Kit, NodeParams, Plugin, PluginParams, RouteProvider, Setup}
import grizzled.slf4j.Logging

class MyFancyPlugin extends Plugin with RouteProvider with Logging {

  var pluginKit: MyFancyPluginKit = _

  override def onKit(kit: Kit): Unit = {
    val fancyActor = kit.system.spawn(Behaviors.supervise(FancyActor(kit.nodeParams)).onFailure(SupervisorStrategy.restart), "my-fancy-plugin-actor")
    pluginKit = MyFancyPluginKit(kit.nodeParams, kit.system, fancyActor)
  }

  override def route(eclairDirectives: EclairDirectives): Route = ApiHandlers.registerRoutes(pluginKit, eclairDirectives)

}

case class MyFancyPluginKit(nodeParams: NodeParams, system: ActorSystem, fancyActor: ActorRef[FancyCommands])
```

We recommend doing the actual route registration in a separate file, for example `ApiHandlers.scala`:

```scala
package fr.acinq.eclair.plugins.myfancyplugin

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.adapter.ClassicSchedulerOps
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.api.serde.FormParamExtractors._
import scodec.bits.ByteVector

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

object ApiHandlers {

  import fr.acinq.eclair.api.serde.JsonSupport.{formats, marshaller, serialization}

  def registerRoutes(kit: MyFancyPluginKit, eclairDirectives: EclairDirectives): Route = {
    import eclairDirectives._

    val doFancyStuff: Route = postRequest("dofancystuff") { implicit t =>
      formFields("fancyId".as[ByteVector32], "fancyData".as[ByteVector].?) {
        (fancyId, fancyData_opt) =>
          val res = kit.fancyActor.ask(ref => FancyCommands.DoFancyStuff(fancyId, fancyData_opt))(Timeout(30 seconds), kit.system.scheduler.toTyped)
          complete(res)
      }
    }

    doFancyStuff
  }

}
```

Your API endpoint will be available as soon as the plugin has been loaded by eclair.
Beware that plugins should make sure they're not registering the same endpoint as other plugins, otherwise there will be a conflict and registration will fail.
