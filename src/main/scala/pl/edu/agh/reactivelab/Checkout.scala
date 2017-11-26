package pl.edu.agh.reactivelab

import java.util.concurrent.TimeUnit

import akka.actor._
import Checkout._
import CartManager.{CheckoutCanceled, CheckoutClosed}
import akka.actor.SupervisorStrategy._
import akka.event.LoggingReceive
import akka.persistence.PersistentActor
import pl.edu.agh.reactivelab.payment.MockHttpProtocol

import scala.concurrent.duration._
import scala.reflect.ClassTag

class Checkout(
  checkoutTimeout: FiniteDuration,
  paymentTimeout: FiniteDuration,
  items: Cart,
  cart: ActorRef,
  customer: ActorRef
) extends PersistentActor with Timers with ActorLogging {

  private object CheckoutTimer
  private object PaymentTimer

  private var lastCheckoutTimer: Option[Long] = _
  private var lastPaymentTimer: Option[Long] = _
  private var selectedDelivery: Option[String] = None
  private var selectedPayment: Option[String] = None

  private def lastTimerValue: Long = {
    lastCheckoutTimer
      .map(_ + checkoutTimeout.toMillis) orElse
    lastPaymentTimer
      .map(_ + paymentTimeout.toMillis) getOrElse
    0L
  }

  private val recoverMessages: PartialFunction[CheckoutMarked, Unit] = {
    case e if e.now > lastTimerValue =>
      log.debug(s"Bug: $e")
      handleCancel()
    case CheckoutMarked(Cancel, _) =>
      handleCancel()
    case CheckoutMarked(DeliveryMethod(method), now) =>
      val deficit = now - lastCheckoutTimer.get
      timers.startSingleTimer(CheckoutTimer, CheckoutTimerExpired, checkoutTimeout minus Duration(deficit, TimeUnit.MILLISECONDS))
      selectedDelivery = Some(method)
      context become selectingPayment(method)
    case CheckoutMarked(PaymentMethod(method), now) =>
      timers.startSingleTimer(PaymentTimer, PaymentTimerExpired, paymentTimeout)
      selectedPayment = Some(method)
      lastCheckoutTimer = None
      lastPaymentTimer = Some(now)
      context.actorOf(PaymentService.props(self, customer))
      context become processingPayment(selectedDelivery.get, method)
    case CheckoutMarked(PaymentReceived, _) =>
      endCheckout(selectedDelivery.get, selectedPayment.get)

  }

  override def preStart(): Unit = {
    timers.startSingleTimer(CheckoutTimer, CheckoutTimerExpired, checkoutTimeout)
    lastCheckoutTimer = Some(System.currentTimeMillis())
  }

  private def selectingDelivery: Receive = cancelBehavior[CheckoutTimerExpired] orElse LoggingReceive {
    case d@DeliveryMethod(method) =>
      persist(d.marked) { _ =>
        context become selectingPayment(method)
      }
  }

  private def cancelBehavior[T <: TimerEvent : ClassTag]: Receive = {
    case Cancel =>
      persist(Cancel.marked) { _ =>
        handleCancel()
      }
    case obj: T =>
      persist(obj) { _ =>
        log.info(s"Timer expired: ${obj.message}")
        handleCancel()
      }
    case "kill" =>
      throw new InterruptedException("Should stop")
  }

  private def handleCancel[T <: TimerEvent : ClassTag](): Unit = {
    cart ! CheckoutCanceled
    log.info("Order has ben canceled")
    context stop self
  }

  private def selectingPayment(
    deliveryMethod: String
  ): Receive = cancelBehavior[CheckoutTimerExpired] orElse LoggingReceive {
    case p@PaymentMethod(paymentMethod) =>
      persist(p.marked) { _ =>
        timers.cancel(CheckoutTimer)
        timers.startSingleTimer(PaymentTimer, PaymentTimerExpired, paymentTimeout)
        context.actorOf(payment.PaymentService.props[MockHttpProtocol](context.self, customer, items))
        context become processingPayment(deliveryMethod, paymentMethod)
      }
  }

  private def processingPayment(
    deliveryMethod: String,
    paymentMethod: String): Receive = cancelBehavior[PaymentTimerExpired] orElse LoggingReceive {
    case PaymentReceived =>
      persist(PaymentReceived.marked) { _ =>
        endCheckout(deliveryMethod, paymentMethod)
      }
  }

  private def endCheckout(deliveryMethod: String, paymentMethod: String): Unit = {
    timers.cancel(PaymentTimer)
    cart ! CheckoutClosed
    log.info(s"Order check-out: {items: $items, delivery: $deliveryMethod, payment: $paymentMethod}")
    context stop self
  }

  override def receiveRecover: Receive = {
    case c: CheckoutMarked =>
      recoverMessages(c)
    case CheckoutTimerExpired =>
      handleCancel[CheckoutTimerExpired]()
    case PaymentTimerExpired =>
      handleCancel[PaymentTimerExpired]()
  }

  override def receiveCommand: Receive = selectingDelivery

  override def persistenceId = s"${cart.toString()}-checkout"

  import payment.PaymentService.Exceptions._
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
    case TimeoutError | (_: ProtocolError) => Stop
    case _: CommunicationError => Restart
  }
}

object Checkout {

  case class CheckoutMarked(event: CheckoutEvent, now: Long = System.currentTimeMillis())

  implicit class ToMarkedEventExtension(private val msg: CheckoutEvent) extends AnyVal {
    def marked = CheckoutMarked(msg)
  }

  import scala.concurrent.duration._

  def props(
    checkoutTimeout: FiniteDuration = 5.minutes,
    paymentTimeout: FiniteDuration = 5.minutes,
    customer: ActorRef)(
    items: Cart,
    cart: ActorRef
  ) = Props(new Checkout(checkoutTimeout, paymentTimeout, items, cart, customer))

  sealed trait CheckoutEvent
  sealed trait TimerEvent {
    def message: String
  }
  case class CheckoutStarted(cart: ActorRef, customer: ActorRef, items: Cart)
  case object Cancel extends CheckoutEvent
  case class DeliveryMethod(name: String) extends CheckoutEvent
  case class PaymentMethod(name: String) extends CheckoutEvent
  case object PaymentReceived extends CheckoutEvent
  case object CheckoutTimerExpired extends TimerEvent {
    override def message: String = "checkout timeout"
  }
  case object PaymentTimerExpired extends TimerEvent {
    override def message: String = "payment timeout"
  }

  sealed trait CheckoutResponse
  case class PaymentServiceStarted(service: ActorRef) extends CheckoutResponse

  type CheckoutTimerExpired = CheckoutTimerExpired.type
  type PaymentTimerExpired = PaymentTimerExpired.type
}
