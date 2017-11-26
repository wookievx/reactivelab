package pl.edu.agh.reactivelab.products

import java.util.concurrent.ThreadLocalRandom

import akka.NotUsed
import akka.stream.scaladsl.Source
import pl.edu.agh.reactivelab.Cart.Item

import scala.collection.immutable.TreeMap
import scala.collection.mutable
import scala.util.Random
import scala.util.control.NonFatal


class DefaultProductStorage extends ProductStorage {

  import DefaultProductStorage._

  import scala.io

  private def itemsFile = io.Source.fromResource("query_result")

  private val rnd = new Random(ThreadLocalRandom.current())
  private val items = itemsFile
    .getLines()
    .drop(1)
    .flatMap { line =>
      val elems = line.split(",").map { s =>
        try {
          s.substring(1, s.length - 1)
        } catch {
          case NonFatal(e) =>
            ""
        }
      }
      elems match {
        case Array(id, name, brand) =>
          Iterator.single(brand -> Item(id, brand, name)(rnd))
        case d if d.length > 3 =>
          Iterator.single(d.last -> Item(d.head, s"${d.slice(1, d.length -1)}", d.last)(rnd))
        case Array(id, p1, p2, p3, brand) =>
          Iterator.single(brand -> Item(id, s"$p1,$p2,$p3", brand)(rnd))
        case Array(id, p1, p2, brand) =>
          Iterator.single(brand -> Item(id, s"$p1,$p2", brand)(rnd))
        case d =>
          println(line + "," + d.length)
          Iterator.empty
      }
    }.groupToTreeMap

  override def get(keyWords: Seq[String], producer: String): Source[Item, NotUsed] = Source fromIterator { () =>
    val stream = for {
      filtered <- items.get(producer).toStream
      item <- filtered.map { i =>
        i -> keyWords.count(i.name.contains)
      }.sortBy(_._2).map(_._1).take(10)
    } yield item
    stream.iterator
  }
}

object DefaultProductStorage {

  implicit class RichIterator[A](private val it: Iterator[(String, A)]) extends AnyVal {
    def groupToTreeMap: TreeMap[String, Stream[A]] = {
      val m = mutable.TreeMap.empty[String, Stream[A]].withDefaultValue(Stream.empty)
      for ((k, v) <- it) {
        m(k) = v #:: m(k)
      }
      val b = TreeMap.newBuilder[String, Stream[A]]
      for ((k, v) <- m)
        b += k -> v
      b.result
    }
  }

}
