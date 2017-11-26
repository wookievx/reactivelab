package pl.edu.agh.reactivelab
import pl.edu.agh.reactivelab.Cart.{Item, ID}

import scala.language.implicitConversions
import scala.util.Random

case class Cart(items: Map[ID, Item]) {
  def addItem(it: Item): Cart = {
    val currentCount = if (items contains it.id) items(it.id).count else 0
    copy(items = items.updated(it.id, it.withCount(_ + currentCount)))
  }

  def removeItem(id: ID, count: Int): Cart = {
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

  implicit def cartAsMap(cart: Cart): Map[ID, Item] = cart.items

  type ID = String

  //uri : brand://name
  case class Item(id: ID, name: String, price: BigDecimal, count: Int) {
    def withCount(modifier: Int => Int): Item = copy(count = modifier(count))
  }

  object Item {
    def apply(id: ID, brand: String, name: String)(random: Random): Item = {
      Item(id, name, random.nextInt(1000), 100)
    }
  }
}
