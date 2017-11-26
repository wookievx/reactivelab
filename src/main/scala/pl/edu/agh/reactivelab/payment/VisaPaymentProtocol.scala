package pl.edu.agh.reactivelab.payment
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.util.ByteString
import pl.edu.agh.reactivelab.Cart

class VisaPaymentProtocol extends PaymentProtocol {
  override type Init = String
  override type Ok = String
  override type Error = String

  def paymentInitializedRequest(order: Cart): HttpRequest = HttpRequest(
    method = HttpMethods.POST,
    uri = s"http://localhost:8080/payment/visa/initlialize?amount=${order.items.values.map(i => i.count * i.price).sum}",
  )
  def paymentProcessingRequest(init: Init): HttpRequest = HttpRequest(
    method = HttpMethods.POST,
    uri = s"http://localhost:8080/payment/visa/process?order=$init"
  )
  override def asInit(msg: ByteString): Option[Init] = Some(new String(msg.toArray))
  override def asOk(msg: ByteString): Option[Ok] = Some(new String(msg.toArray))
  override def asError(msg: ByteString): Option[Error] = Some(new String(msg.toArray))
}
