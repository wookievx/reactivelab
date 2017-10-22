package pl.edu.agh.reactivelab

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.event.LoggingReceive
import pl.edu.agh.reactivelab.Cart.{ItemAdded, ItemRemoved}
import Cart._

import scala.concurrent.duration._

class Cart(defaultTimeout: FiniteDuration) extends Actor with Timers with ActorLogging {

  private def startDefaultTimer() = timers.startSingleTimer("unique", CartTimerExpired, defaultTimeout)
  private def cancelDefaultTimer() = timers.cancel("unique")
  private val emptyCart: Receive = LoggingReceive {
    case ItemAdded(item) =>
      startDefaultTimer()
      context become nonEmptyCart(Set(item))

  }
  private def nonEmptyCart(items: Set[Any]): Receive = LoggingReceive {
    case ItemAdded(item) =>
      startDefaultTimer()
      context become nonEmptyCart(items + item)
    case ItemRemoved(item) if items.size > 1 =>
      startDefaultTimer()
      context become nonEmptyCart(items - item)
    case ItemRemoved(_) =>
      context become emptyCart
    case CartTimerExpired =>
      context become emptyCart
    case CheckoutStarted =>
      val checkout = context.actorOf(Checkout.props(defaultTimeout, defaultTimeout))
      checkout ! Checkout.CheckoutStarted(self, items)
      sender ! CheckoutStartedResponse(checkout)
      cancelDefaultTimer()
      context become inCheckout(checkout, items)
  }

  private def inCheckout(checkout: ActorRef, items: Set[Any]): Receive = LoggingReceive {
    case CheckoutClose =>
      context become emptyCart
    case CheckoutCanceled =>
      startDefaultTimer()
      context become nonEmptyCart(items)

  }


  override def receive: Receive = emptyCart
}

object Cart {



  def props: Props = Props(new Cart(5.minutes))

  sealed trait CartEvent

  case object CartTimerExpired extends CartEvent

  case class ItemAdded[T](item: T) extends CartEvent
  case class ItemRemoved[T](item: T) extends CartEvent
  case object CheckoutStarted extends CartEvent
  case object CheckoutCanceled extends CartEvent
  case object CheckoutClose extends CartEvent

  sealed trait CartResponse
  case class CheckoutStartedResponse(checkout: ActorRef)

}
