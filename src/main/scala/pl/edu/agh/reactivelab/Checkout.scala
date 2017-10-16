package pl.edu.agh.reactivelab

import akka.actor.{Actor, Timers}

class Checkout extends Actor with Timers{
  override def receive: Receive = ???
}

object Checkout {
  sealed trait CheckoutEvent
  case object Canceled extends CheckoutEvent
  case object CheckoutTimerExceeded extends CheckoutEvent
  case class DeliveryMethod(name: String) extends CheckoutEvent
  case object PaymentTimerExceeded extends CheckoutEvent
  case class PaymentMethod(name: String) extends CheckoutEvent
  case object PaymentAccepted extends CheckoutEvent
}
