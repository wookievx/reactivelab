package pl.edu.agh.reactivelab

import akka.actor._
import Checkout._
import Cart.{CheckoutCanceled, CheckoutClose}
import akka.event.LoggingReceive
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

class Checkout(checkoutTimeout: FiniteDuration, paymentTimeout: FiniteDuration) extends Actor with Timers with ActorLogging {

  private object CheckoutTimer
  private object PaymentTimer

  private val initialBehaviour: Receive = LoggingReceive {
    case CheckoutStarted(cart, items) =>
      timers.startSingleTimer(CheckoutTimer, CheckoutTimerExpired, checkoutTimeout)
      context become selectingDelivery(cart, items)
  }

  private def selectingDelivery(
    cart: ActorRef,
    items: Set[Any]
  ): Receive = cancelBehavior[CheckoutTimerExpired](cart) orElse LoggingReceive {
    case DeliveryMethod(method) =>
      context become selectingPayment(cart, items, method)
  }

  private def cancelBehavior[T <: TimerEvent : ClassTag](cart: ActorRef): Receive = {
    case Canceled =>
      cart ! CheckoutCanceled
      log.info("Order has ben canceled")
      context stop self
    case obj: T =>
      cart ! CheckoutCanceled
      log.info(s"Timer expired: ${obj.message}")
      context stop self
  }

  private def selectingPayment(
    cart: ActorRef,
    items: Set[Any],
    deliveryMethod: String
  ): Receive = cancelBehavior[CheckoutTimerExpired](cart) orElse LoggingReceive {
    case PaymentMethod(paymentMethod) =>
      timers.cancel(CheckoutTimer)
      timers.startSingleTimer(PaymentTimer, PaymentTimerExpired, paymentTimeout)
      context become processingPayment(cart, items, deliveryMethod, paymentMethod)
  }

  private def processingPayment(
    cart: ActorRef,
    items: Set[Any],
    deliveryMethod: String,
    paymentMethod: String): Receive = cancelBehavior[PaymentTimerExpired](cart) orElse LoggingReceive {
    case PaymentReceived =>
      timers.cancel(PaymentTimer)
      cart ! CheckoutClose
      log.info(s"Order check-out: {items: $items, delivery: $deliveryMethod, payment: $paymentMethod}")
      context stop self
  }

  override def receive: Receive = initialBehaviour
}

object Checkout {

  def props(
    checkoutTimeout: FiniteDuration,
    paymentTimeout: FiniteDuration
  ) = Props(new Checkout(checkoutTimeout, paymentTimeout))

  sealed trait CheckoutEvent
  sealed trait TimerEvent {
    def message: String
  }
  case class CheckoutStarted(cart: ActorRef, items: Set[Any])
  case object Canceled extends CheckoutEvent
  case class DeliveryMethod(name: String) extends CheckoutEvent
  case class PaymentMethod(name: String) extends CheckoutEvent
  case object PaymentReceived extends CheckoutEvent
  case object CheckoutTimerExpired extends TimerEvent {
    override def message: String = "checkout timeout"
  }
  case object PaymentTimerExpired extends TimerEvent {
    override def message: String = "payment timeout"
  }

  type CheckoutTimerExpired = CheckoutTimerExpired.type
  type PaymentTimerExpired = PaymentTimerExpired.type
}
