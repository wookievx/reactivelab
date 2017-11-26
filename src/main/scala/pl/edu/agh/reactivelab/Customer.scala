package pl.edu.agh.reactivelab

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.event.LoggingReceive
import pl.edu.agh.reactivelab.Cart.Item
import pl.edu.agh.reactivelab.products.ProductCatalog.ProductQuery

import scala.concurrent.duration._

class Customer(storage: ActorRef) extends Actor with Timers with ActorLogging {
  import CartManager._
  import Checkout.PaymentServiceStarted
  import PaymentService._

  private var cart: ActorRef = _

  override def preStart(): Unit = {
    cart = context.actorOf(CartManager.props(self)(Checkout.props(5.minutes, 5.minutes, self)))
    storage ! ProductQuery(List("Fanta", "Pepsi"), "Fanta")
  }

  override def postStop(): Unit = {
    context.stop(cart)
  }

  private def loggedIn(cart: ActorRef): Receive = LoggingReceive {
    case CheckoutStarted(checkout) =>
      context become inCheckout(cart, checkout)
    case CartEmpty =>
    case i: Item =>
      cart ! ItemAdded(i)
  }

  private def inCheckout(cart: ActorRef, checkout: ActorRef): Receive = LoggingReceive {
    case CheckoutClosed | CheckoutCanceled =>
      context become loggedIn(cart)
    case PaymentServiceStarted(service) =>
      context become inPayment(cart, checkout, service)
  }

  private def inPayment(cart: ActorRef, checkout: ActorRef, payment: ActorRef): Receive = LoggingReceive {
    case CheckoutClosed | CheckoutCanceled =>
      context become loggedIn(cart)
    case PaymentConfirmed =>
      context become loggedIn(cart)
  }

  override def receive: Receive = loggedIn(cart)
}

object Customer {
  def props(storage: ActorRef) = Props(new Customer(storage))
}
