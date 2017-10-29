package pl.edu.agh.reactivelab

import akka.actor.{ActorSystem, Props}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit, TestProbe}
import org.scalatest.{AsyncFlatSpec, FlatSpecLike, Matchers}
import pl.edu.agh.reactivelab.Cart.CheckoutClosed


class CartTest extends TestKit(ActorSystem(
  "CartTest"
)) with DefaultTimeout with ImplicitSender with FlatSpecLike with Matchers {

  private val probeCheckout = TestProbe()

  "Cart" should "store items correctly" in {
    import Cart._
    val probe = TestProbe()
    val customerProbe = TestProbe()
    val cart = system.actorOf(Cart.props(customerProbe.testActor))

    cart ! ItemAdded("i1")
    cart ! ItemAdded("i2")
    cart ! ItemAdded("i3")
    (cart ! StartCheckout)(probe.testActor)
    probe expectMsg Set("i1", "i2", "i3")

  }

  "Checkout" should "react correctly" in {
    import Checkout._
    import scala.concurrent.duration._

    val probeCart = TestProbe()
    val probeCustomer = TestProbe()

    val checkout = system.actorOf(Checkout.props(5.minutes, 5.minutes))
    checkout ! CheckoutStarted(probeCart.testActor, probeCustomer.testActor, Set(1, 2, 3))
    checkout ! DeliveryMethod("post-office")
    checkout ! PaymentMethod("pay-pal")
    checkout ! PaymentReceived
    probeCart.expectMsg(CheckoutClosed)

  }


}
