package pl.edu.agh.reactivelab.payment

import akka.http.scaladsl.model.HttpRequest
import akka.util.ByteString
import pl.edu.agh.reactivelab.Cart

trait PaymentProtocol {
  type Init
  type Ok
  type Error
  def paymentInitializedRequest(order: Cart): HttpRequest
  def paymentProcessingRequest(init: Init): HttpRequest
  def asInit(msg: ByteString): Option[Init]
  def asOk(msg: ByteString): Option[Ok]
  def asError(msg: ByteString): Option[Error]
}

object PaymentProtocol {

  trait PaymentProtocolUtils[T <: PaymentProtocol] {
    implicit val protocol: T
    val Init = new UnapplyForwarder(isInit(_))
    val Ok = new UnapplyForwarder(isOk(_))
    val Error = new UnapplyForwarder(isError(_))
    def initRequest(order: Cart): HttpRequest = protocol.paymentInitializedRequest(order)
    def paymentProcessingRequest: protocol.Init => HttpRequest = protocol.paymentProcessingRequest
  }

    class UnapplyForwarder[T](extractor: ByteString => Option[T]) {
    def unapply(arg: ByteString): Option[T] = extractor(arg)
  }

  def isInit(arg: ByteString)(implicit ev: PaymentProtocol): Option[ev.Init] = ev.asInit(arg)
  def isOk(arg: ByteString)(implicit ev: PaymentProtocol): Option[ev.Ok] = ev.asOk(arg)
  def isError(arg: ByteString)(implicit ev: PaymentProtocol): Option[ev.Error] = ev.asError(arg)
}
