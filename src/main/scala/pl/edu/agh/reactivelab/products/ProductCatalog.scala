package pl.edu.agh.reactivelab.products

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import ProductCatalog._

class ProductCatalog(
  productStorage: ProductStorage)(implicit
  materializer: Materializer
) extends Actor with ActorLogging {

  override def receive: Receive = {
    case ProductQuery(keywords, producer) =>
      productStorage
        .get(keywords, producer)
        .runWith(Sink.actorRef(sender(), End))
  }
}

object ProductCatalog {
  def props(productStorage: ProductStorage)(implicit materializer: Materializer) = Props(new ProductCatalog(productStorage))
  case class ProductQuery(keywords: List[String], producer: String)
  case object End
}
