package pl.edu.agh.reactivelab

import akka.actor.{ActorRef, FSM, Props}
import pl.edu.agh.reactivelab.Cart.{CheckoutCanceled, CheckoutClosed}
import pl.edu.agh.reactivelab.Checkout._
import pl.edu.agh.reactivelab.CheckoutFSM.{Canceled, _}

import scala.concurrent.duration._

class CheckoutFSM(checkoutTimeout: FiniteDuration, paymentTimeout: FiniteDuration) extends FSM[CheckoutState, Data]{

  startWith(Initial, Ignored)

  when(Initial) {
    case Event(CheckoutStarted(cart, _, items), _) =>
      log.info(s"Started checkout: $items")
      goto(SelectingDelivery) using SelectingDeliveryData(items, cart)
  }

  when(SelectingDelivery) {
    case Event(DeliveryMethod(delivery), d: SelectingDeliveryData) =>
      log.info(s"Selected delivery: $delivery")
      goto(SelectingPayment) using (d withDelivery delivery)
  }

  when(SelectingPayment) {
    case Event(PaymentMethod(payment), d: SelectingPaymentData) =>
      log.info(s"Selected payment: $payment")
      goto(ProcessingPayment) using (d withPayment payment)
  }

  when(ProcessingPayment) {
    case Event(PaymentReceived, ProcessingPaymentData(delivery, payment, elems, cart)) =>
      log.info(s"Received payment, for order: [$elems, $delivery, $payment]")
      cart ! CheckoutClosed
      goto(Closed) using Ignored
  }

  when(Closed)(FSM.NullFunction)
  when(Canceled)(FSM.NullFunction)

  whenUnhandled {
    case Event(Checkout.Cancel, InitializedState(cart)) =>
      log.info("Order has been canceled")
      cart ! CheckoutCanceled
      goto(CheckoutFSM.Canceled) using Ignored
    case Event(t: TimerEvent, InitializedState(cart)) =>
      log.info(s"Checkout timed-out: ${t.message}")
      cart ! CheckoutCanceled
      goto(CheckoutFSM.Canceled) using Ignored
  }

  onTransition {
    case _ -> CheckoutFSM.Canceled =>
      context.stop(self)
    case ProcessingPayment -> CheckoutFSM.Closed =>
      cancelTimer(paymentTimer)
      context.stop(self)
    case Initial -> SelectingDelivery =>
      setTimer(checkoutTimer, CheckoutTimerExpired, checkoutTimeout)
    case SelectingPayment -> ProcessingPayment =>
      cancelTimer(checkoutTimer)
      setTimer(paymentTimer, PaymentTimerExpired, paymentTimeout)
  }


  private val checkoutTimer = "checkout"
  private val paymentTimer = "paymnet"

}


object CheckoutFSM {

  def props(
    checkoutTimeout: FiniteDuration,
    paymentTimeout: FiniteDuration
  ) = Props(new CheckoutFSM(checkoutTimeout, paymentTimeout))

  sealed trait Data
  sealed trait InitializedState extends Data {
    def cart: ActorRef
  }
  case object Ignored extends Data
  case class SelectingDeliveryData(elems: Set[Any], cart: ActorRef) extends InitializedState {
    def withDelivery(delivery: String) = SelectingPaymentData(delivery, elems, cart)
  }
  case class SelectingPaymentData(delivery: String, elems: Set[Any], cart: ActorRef) extends InitializedState {
    def withPayment(payment: String) = ProcessingPaymentData(delivery, payment, elems, cart)
  }
  case class ProcessingPaymentData(delivery: String, payment: String, elems: Set[Any], cart: ActorRef) extends InitializedState

  object InitializedState {
    def unapply(arg: Data): Option[ActorRef] = arg match {
      case i: InitializedState => Some(i.cart)
      case _ => None
    }
  }

  sealed trait CheckoutState
  case object Initial extends CheckoutState
  case object SelectingDelivery extends CheckoutState
  case object SelectingPayment extends CheckoutState
  case object ProcessingPayment extends CheckoutState
  case object Canceled extends CheckoutState
  case object Closed extends CheckoutState

}