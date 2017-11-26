package pl.edu.agh.reactivelab.payment

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.{ClientError, ServerError}
import akka.http.scaladsl.model.{HttpResponse, ResponseEntity, StatusCodes}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import pl.edu.agh.reactivelab.Cart
import pl.edu.agh.reactivelab.Checkout.PaymentReceived
import pl.edu.agh.reactivelab.payment.PaymentProtocol._
import pl.edu.agh.reactivelab.payment.PaymentService._

import scala.concurrent.Future
import scala.util.control.NonFatal

class PaymentService[T <: PaymentProtocol](
  checkout: ActorRef,
  customer: ActorRef,
  cart: Cart)(implicit
  val protocol: T
) extends PaymentProtocolUtils[T] with Actor with ActorLogging with Timers {

  import akka.pattern.pipe
  import context.dispatcher

  import scala.concurrent.duration._
  import Exceptions._

  private implicit val materialzier: ActorMaterializer = ActorMaterializer()
  private val http = Http(context.system)

  override def preStart: Unit = {
    timers.startSingleTimer("t", PaymentTimeout, 5.minutes)
    http.singleRequest(initRequest(cart))
      .pipeTo(self)
  }

  private def entityToByteString(entity: ResponseEntity): Future[ByteString] = {
    entity.dataBytes.runFold(ByteString(""))(_ ++ _)
  }

  private def initializing: Receive = {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      entityToByteString(entity) recover {
        case NonFatal(t) =>
          log.error(t, "Error during consuming server response")
          CommunicationError("Failed to establish communication during initialization", t)
      } pipeTo self
    case Init(msg) =>
      http.singleRequest(paymentProcessingRequest apply msg).pipeTo(self)
      context become processing
  }

  private def processing: Receive = errorFlow orElse {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      entityToByteString(entity) recover {
        case NonFatal(t) =>
          log.error(t, "Error during consuming server response")
          CommunicationError("Failed to establish communication during processing", t)
      } pipeTo self
    case Ok(msg) =>
      customer ! PaymentConfirmed
      checkout ! PaymentReceived
      context stop self
  }

  def receive: Receive = initializing

  private def errorFlow: Receive = {
    case resp @ HttpResponse(c@(_: ClientError |_: ServerError), _, _, _) =>
      log.info("Request failed, response code: " + c)
      resp.discardEntityBytes()
    case t: CommunicationError =>
      throw t
    case Error(msg) =>
      throw ProtocolError(msg.toString)
    case PaymentTimeout =>
      throw TimeoutError
  }
}

object PaymentService {

  def props[T <: PaymentProtocol](
    checkout: ActorRef,
    customer: ActorRef,
    cart: Cart)(implicit
    ev: T
  ) = Props(new PaymentService(checkout, customer, cart))

  object Exceptions {
    case class ProtocolError(msg: String) extends Exception(msg)
    case object TimeoutError extends Exception("Payment timed-out")
    case class CommunicationError(msg: String, reason: Throwable) extends Exception(msg)
  }


  private object PaymentTimeout
  sealed trait PaymentResponse
  case object PaymentConfirmed extends PaymentResponse
}


