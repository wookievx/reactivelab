package pl.edu.agh.reactivelab

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.event.LoggingReceive
import pl.edu.agh.reactivelab.Cart.{ItemAdded, ItemRemoved}
import Cart._

import scala.concurrent.duration._

class Cart(
  defaultTimeout: FiniteDuration,
  checkoutProps: (FiniteDuration, FiniteDuration) => Props,
  customer: ActorRef) extends Actor with Timers with ActorLogging {

  private def startDefaultTimer() = timers.startSingleTimer("unique", CartTimerExpired, defaultTimeout)
  private def cancelDefaultTimer() = timers.cancel("unique")
  private def emptyCart: Receive = LoggingReceive {
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
      customer ! CartEmpty
      context become emptyCart
    case CartTimerExpired =>
      customer ! CartEmpty
      context become emptyCart
    case StartCheckout =>
      val checkout = context.actorOf(checkoutProps(defaultTimeout, defaultTimeout))
      checkout ! Checkout.CheckoutStarted(self, customer, items)
      customer ! CheckoutStarted(checkout)
      //only for test
      sender ! items
      cancelDefaultTimer()
      context become inCheckout(checkout, customer, items)
  }

  private def inCheckout(checkout: ActorRef, customer: ActorRef, items: Set[Any]): Receive = LoggingReceive {
    case CheckoutClosed =>
      customer ! CheckoutClosed
      context become emptyCart
    case CheckoutCanceled =>
      customer ! CheckoutCanceled
      startDefaultTimer()
      context become nonEmptyCart(items)
  }


  override def receive: Receive = emptyCart
}

object Cart {



  def props(
    customer: ActorRef,
    checkoutProps: (FiniteDuration, FiniteDuration) => Props = Checkout.props
  ): Props = Props(new Cart(5.minutes, checkoutProps, customer))

  sealed trait CartEvent

  case object CartTimerExpired extends CartEvent

  case class ItemAdded[T](item: T) extends CartEvent
  case class ItemRemoved[T](item: T) extends CartEvent
  case object StartCheckout extends CartEvent
  case object CheckoutCanceled extends CartEvent
  case object CheckoutClosed extends CartEvent

  sealed trait CartResponse
  case class CheckoutStarted(checkout: ActorRef) extends CartResponse
  case object CartEmpty extends CartResponse



}
