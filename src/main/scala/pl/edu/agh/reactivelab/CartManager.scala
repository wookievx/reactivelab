package pl.edu.agh.reactivelab

import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, ActorRef, Props, Timers}
import akka.event.LoggingReceive
import pl.edu.agh.reactivelab.CartManager.{ItemAdded, ItemRemoved}
import CartManager._
import akka.persistence.{PersistentActor, SnapshotOffer}
import pl.edu.agh.reactivelab.Cart.Item

import scala.concurrent.duration._

class CartManager(
  defaultTimeout: FiniteDuration,
  checkoutProps: (Cart, ActorRef) => Props,
  customer: ActorRef
) extends PersistentActor with Timers with ActorLogging {

  private var recoverState: RecoverState = RecoverState(Empty, Cart.empty, None)

  private def startDefaultTimer(): Unit =  {
    timers.startSingleTimer("unique", CartTimerExpired, defaultTimeout)
  }

  private def startDefaultTimer(deficit: Long): Unit = {
    timers.startSingleTimer("unique", CartTimerExpired, defaultTimeout minus Duration(deficit, TimeUnit.MILLISECONDS))
  }

  private def cancelDefaultTimer(): Unit = timers.cancel("unique")


  private def recoverMethod: PartialFunction[MarkedEvent, Unit] = {
    case event if event.now < recoverState.timerStart.map(_ + defaultTimeout.toMillis).getOrElse(Long.MaxValue) =>
      cancelDefaultTimer()
      recoverState = RecoverState(Empty, Cart.empty)
      context become emptyCart
    case MarkedEvent(ItemAdded(item), now) =>
      recoverState = recoverState.copy(cart = recoverState.cart addItem item, timerStart = Some(now))
      startDefaultTimer()
      context become nonEmptyCart(recoverState.cart)
    case MarkedEvent(ItemRemoved(item), now) =>
      recoverState = recoverState.copy(cart = recoverState.cart addItem item)
      startDefaultTimer(recoverState.timerStart.map(now - _).getOrElse(0))
      context become nonEmptyCart(recoverState.cart)
    case MarkedEvent(CartTimerExpired, _) =>
      cancelDefaultTimer()
      recoverState = RecoverState(Empty, Cart.empty, None)
      context become emptyCart
  }

  private def initializeCheckout(items: Cart): Unit = {
    val checkout = context.actorOf(checkoutProps(items, self))
    checkout ! Checkout.CheckoutStarted(self, customer, recoverState.cart)
    customer ! CheckoutStarted(checkout)
    cancelDefaultTimer()
    context become inCheckout(checkout, items)
  }

  private def emptyCart: Receive = {
    log.debug("Entered state empty")
    LoggingReceive {
      case event@ItemAdded(item) =>
        persist(event.marked) { _ =>
          startDefaultTimer()
          context become nonEmptyCart(Cart.empty addItem item)
        }
    }
  }

  private def snapshotCart(cart: Cart, event: TimerEvent)(code: => Unit): Unit = persist(event) { _ =>
    code
    saveSnapshot(CartSnapshot(event, NonEmpty, cart))
  }

  private def nonEmptyCart(items: Cart): Receive = {
    log.debug(s"Entered state NonEmpty: $items")
    LoggingReceive {
      case ItemAdded(item) if items.size > 5 && items.size % 5 == 0 =>
        snapshotCart(items, TimerStarted()) {
          startDefaultTimer()
          context become nonEmptyCart(items addItem item)
        }
      case event@ItemAdded(item) =>
        persist(event.marked) { _ =>
          persist(TimerStarted()) { _ =>
            startDefaultTimer()
            context become nonEmptyCart(items addItem item)
          }
        }
      case event@ItemRemoved(item) =>
        persist(event.marked) { _ =>
          persist(TimerStarted()) { _ =>
            startDefaultTimer()
            val cart = items.removeItem(item.id, 1)
            if (cart.nonEmpty) {
              context become nonEmptyCart(items.removeItem(item.id, item.count))
            } else {
              context become emptyCart
            }
          }
        }
      case CartTimerExpired =>
        persist(CartTimerExpired) { _ =>
          context become emptyCart
        }
      case StartCheckout =>
        persist(StartCheckout.marked) { _ =>
          sender ! items
          initializeCheckout(items)
          saveSnapshot(CartSnapshot(TimerCanceled, InCheckout, items))
        }
    }
  }

  private def inCheckout(checkout: ActorRef, items: Cart): Receive = {
    log.debug(s"Entered state InCheckout: $items")
    LoggingReceive {
      case CheckoutClosed =>
        persist(CheckoutClosed.marked) { _ =>
          customer ! CheckoutClosed
          context become emptyCart
          saveSnapshot(CartSnapshot(NoTimerEvent, Empty, Cart.empty))
        }
      case CheckoutCanceled =>
        persist(CheckoutCanceled.marked) { _ =>
          customer ! CheckoutCanceled
          startDefaultTimer()
          context become nonEmptyCart(items)
          saveSnapshot(CartSnapshot(TimerStarted(), NonEmpty, Cart.empty))
        }
    }
  }

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, CartSnapshot(_, Empty, _)) =>
      recoverState = RecoverState(Empty, Cart.empty)
      context become emptyCart
    case SnapshotOffer(_, CartSnapshot(event: TimerStarted, NonEmpty, cart)) =>
      recoverState = RecoverState(NonEmpty, cart, Some(event.now))
      startDefaultTimer()
      context become nonEmptyCart(cart)
    case SnapshotOffer(_, CartSnapshot(_, InCheckout, cart)) =>
      recoverState = RecoverState(InCheckout, cart)
      initializeCheckout(cart)
    case e: MarkedEvent =>
      recoverMethod(e)
  }

  override def receiveCommand: Receive = emptyCart

  override def persistenceId: String = s"${customer.toString()}-cart"
}

object CartManager {

  case class CartSnapshot(
    timerStarted: TimerEvent,
    cartState: CartState,
    cart: Cart
  )

  case class RecoverState(
    cartState: CartState,
    cart: Cart,
    timerStart: Option[Long] = None
  )

  sealed trait CartState
  case object Empty extends CartState
  case object NonEmpty extends CartState
  case object InCheckout extends CartState

  def props(
    customer: ActorRef)(
    checkoutProps: (Cart, ActorRef) => Props = Checkout.props(customer = customer)
  ): Props = Props(new CartManager(5.minutes, checkoutProps, customer))

  sealed trait CartMessage
  sealed trait CartEvent
  sealed trait TimerEvent extends CartEvent

  case object CartTimerExpired extends CartMessage

  case class MarkedEvent(message: CartMessage, now: Long = System.currentTimeMillis()) extends CartEvent

  implicit class ToMarkedEventExtension(private val msg: CartMessage) extends AnyVal {
    def marked = MarkedEvent(msg)
  }

  case class TimerStarted(now: Long = System.currentTimeMillis()) extends TimerEvent
  case object TimerCanceled extends TimerEvent
  case object NoTimerEvent extends TimerEvent

  case class ItemAdded(item: Item) extends CartMessage
  case class ItemRemoved(item: Item) extends CartMessage
  case object StartCheckout extends CartMessage
  case object CheckoutCanceled extends CartMessage
  case object CheckoutClosed extends CartMessage

  sealed trait CartResponse
  case class CheckoutStarted(checkout: ActorRef) extends CartResponse
  case object CartEmpty extends CartResponse



}
