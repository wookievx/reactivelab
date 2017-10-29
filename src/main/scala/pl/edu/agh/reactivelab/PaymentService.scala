package pl.edu.agh.reactivelab

import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.event.LoggingReceive
import pl.edu.agh.reactivelab.Checkout.PaymentReceived
import scala.concurrent.duration._

class PaymentService(checkout: ActorRef, customer: ActorRef) extends Actor with Timers {
  import PaymentService._

  timers.startSingleTimer("t", PaymentTimeout, 5.minutes)

  override def receive: Receive = LoggingReceive {
    case DoPayment =>
      customer ! PaymentConfirmed
      checkout ! PaymentReceived
      context stop self
    case PaymentTimeout =>
      context stop self
  }
}

object PaymentService {

  def props(checkout: ActorRef, customer: ActorRef) = Props(new PaymentService(checkout, customer))

  private object PaymentTimeout

  sealed trait PaymentResponse
  case object PaymentConfirmed extends PaymentResponse

  sealed trait PaymentCommand
  case object DoPayment extends PaymentCommand

}
