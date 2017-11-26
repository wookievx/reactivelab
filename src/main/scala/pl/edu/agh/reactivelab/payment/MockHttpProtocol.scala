package pl.edu.agh.reactivelab.payment
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.util.ByteString
import pl.edu.agh.reactivelab.Cart

class MockHttpProtocol extends PaymentProtocol {
  override type Init = String
  override type Ok = String
  override type Error = String

  override def paymentInitializedRequest(order: Cart): HttpRequest = HttpRequest(
    method = HttpMethods.POST,
    uri = s"http://localhost:8080/payment/initlialize?amount=${order.items.values.map(i => i.count * i.price).sum}",
  )

  override def paymentProcessingRequest(msg: Init): HttpRequest = HttpRequest(
    method = HttpMethods.POST,
    uri = s"http://localhost:8080/payment/process?order=$msg"
  )

  override def asInit(msg: ByteString): Option[Init] = Some(new String(msg.toArray))

  override def asOk(msg: ByteString): Option[Ok] = Some(new String(msg.toArray))

  override def asError(msg: ByteString): Option[Error] = Some(new String(msg.toArray))
}

object MockHttpProtocol {
  implicit def instance: MockHttpProtocol = new MockHttpProtocol
}
