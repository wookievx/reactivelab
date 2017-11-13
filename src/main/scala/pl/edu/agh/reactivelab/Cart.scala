package pl.edu.agh.reactivelab

import java.net.URI

import pl.edu.agh.reactivelab.Cart.Item

import scala.language.implicitConversions

case class Cart(items: Map[URI, Item]) {
  def addItem(it: Item): Cart = {
    val currentCount = if (items contains it.id) items(it.id).count else 0
    copy(items = items.updated(it.id, it.withCount(_ + currentCount)))
  }

  def removeItem(id: URI, count: Int): Cart = {
    val maybeItem = items.get(id)
    val nextItems = maybeItem.filter(_.count >= count).map { item =>
      items.updated(id, item.withCount(_ - count))
    } getOrElse {
      items - id
    }
    copy(items = nextItems)
  }
}

object Cart {

  def empty = Cart(Map.empty)

  implicit def cartAsMap(cart: Cart): Map[URI, Item] = cart.items

  case class Item(id: URI, name: String, price: BigDecimal, count: Int) {
    def withCount(modifier: Int => Int): Item = copy(count = modifier(count))
  }
}
