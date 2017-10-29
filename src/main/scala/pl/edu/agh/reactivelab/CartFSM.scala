package pl.edu.agh.reactivelab

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import CartFSM._
import Cart._

import scala.concurrent.duration._

class CartFSM(defaultTimeout: FiniteDuration) extends FSM[CartState, Data] with ActorLogging {

  startWith(Empty, EmptyData)

  when(Empty) {
    case Event(ItemAdded(item), _) =>
      log.info(s"Received item: $item")
      goto(NonEmpty) using NonEmptyData(Set(item))
  }

  when(NonEmpty) {
    case Event(ItemAdded(item), d: NonEmptyData) =>
      log.info(s"Received item: $item")
      goto(NonEmpty) using (d withItem item)
    case Event(ItemRemoved(item), d@NonEmptyData(elems)) if elems.size > 1 =>
      log.info(s"Removed item: $item")
      goto(NonEmpty) using (d withoutItem item)
    case Event(ItemRemoved(item), _) =>
      log.info(s"Removed item: $item")
      goto(Empty) using EmptyData
    case Event(CartTimerExpired, _) =>
      log.info("Cart timer expired")
      goto(Empty) using EmptyData
    case Event(StartCheckout, NonEmptyData(elems)) =>
      log.info("Checkout started")
      val checkout = context.actorOf(CheckoutFSM.props(defaultTimeout, defaultTimeout))
      checkout ! Checkout.CheckoutStarted(self, ???, elems)
      goto(InCheckout) using InCheckoutData(elems, checkout) replying CheckoutStarted(checkout)
  }

  when(InCheckout) {
    case Event(CheckoutClosed, _) =>
      goto(Empty) using EmptyData
    case Event(CheckoutCanceled, InCheckoutData(items, _)) =>
      goto(NonEmpty) using NonEmptyData(items)
  }

  onTransition {
    case Empty -> NonEmpty =>
      setTimer("unique", CartTimerExpired, defaultTimeout)
    case InCheckout -> NonEmpty =>
      setTimer("unique", CartTimerExpired, defaultTimeout)
  }

  initialize()

}

object CartFSM {

  def props(defaultTimeout: FiniteDuration) = Props(new CartFSM(defaultTimeout))

  sealed trait Data
  case object EmptyData extends Data
  case class NonEmptyData(items: Set[Any]) extends Data {
    def withItem(item: Any) = NonEmptyData(items + item)
    def withoutItem(item: Any) = NonEmptyData(items - item)
  }
  case class InCheckoutData(items: Set[Any], checkout: ActorRef) extends Data

  sealed trait CartState
  case object Empty extends CartState with Data
  case object NonEmpty extends CartState
  case object InCheckout extends CartState
}
