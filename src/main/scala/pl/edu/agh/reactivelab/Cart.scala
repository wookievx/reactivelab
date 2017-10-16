package pl.edu.agh.reactivelab

import akka.actor.{Actor, Props, Timers}
import akka.event.LoggingReceive
import pl.edu.agh.reactivelab.Cart.{ItemAdded, ItemRemoved}
import Cart._

import scala.concurrent.duration._

class Cart extends Actor with Timers {

  private def defaultTimer() = timers.startSingleTimer("unique", CartTimerExpired, 5.minutes)
  private val emptyCart: LoggingReceive = {
    case ItemAdded(item) =>
      defaultTimer()
      context become nonEmptyCart(Set(item))

  }
  private def nonEmptyCart(items: Set[Any]): LoggingReceive = {
    case ItemAdded(item) =>
      defaultTimer()
      context become nonEmptyCart(items + item)
    case ItemRemoved(item) if items.size > 1 =>
      defaultTimer()
      context become nonEmptyCart(items - item)
    case ItemRemoved(item) =>
      context become emptyCart
    case CartTimerExpired =>
      context become emptyCart
    case CheckoutStarted =>
      context become inCheckout(items)
  }

  private def inCheckout(items: Set[Any]): LoggingReceive = {
    case CheckoutClose =>
      context become emptyCart
    case CheckoutCanceled =>
      context become nonEmptyCart(items)

  }


  override def receive: Receive = emptyCart
}

object Cart {

  def props: Props = Props(new Cart)

  sealed trait CartEvent

  object <<*>< {
    def unapply(arg: Any): Option[(Int, Int)] = arg match {
      case "working" => Some(42 -> 69)
      case _ => None
    }
  }

  case object CartTimerExpired extends CartEvent

  case class ItemAdded[T](item: T) extends CartEvent
  case class ItemRemoved[T](item: T) extends CartEvent
  case object CheckoutStarted extends CartEvent
  case object CheckoutCanceled extends CartEvent
  case object CheckoutClose extends CartEvent

}
