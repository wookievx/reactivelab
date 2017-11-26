package pl.edu.agh.reactivelab.products

import java.net.URI

import akka.NotUsed
import akka.stream.scaladsl.Source
import pl.edu.agh.reactivelab.Cart.Item

trait ProductStorage {
  def get(keyWords: Seq[String], producer: String): Source[Item, NotUsed]
}

object ProductStorage {

}
