package pl.edu.agh.reactivelab.launcher

import akka.actor.{ActorSystem, Terminated}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import pl.edu.agh.reactivelab.products.{DefaultProductStorage, ProductCatalog, ProductStorage}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class ProductCatalogLauncher {
  private val config = ConfigFactory.load()
    .atPath("storage")

  private implicit val actorSystem: ActorSystem = ActorSystem("storage", config)
  private implicit val materialzier: ActorMaterializer = ActorMaterializer()

  def launch: Future[Terminated] = {
    //heavy operation
    actorSystem.actorOf(ProductCatalog.props(new DefaultProductStorage), "catalog")
    actorSystem.whenTerminated
  }

}

object ProductCatalogLauncher {

  def main(args: Array[String]): Unit = {
    val launcher = new ProductCatalogLauncher
    Await.result(launcher.launch, Duration.Inf)
  }

}
