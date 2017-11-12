package pl.edu.agh.reactivelab

import java.net.URI

import akka.actor.ActorSystem
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit, TestProbe}
import org.scalatest.{FlatSpecLike, Matchers}
import pl.edu.agh.reactivelab.Cart.Item
import pl.edu.agh.reactivelab.CartManager.CheckoutClosed


class CartManagerTest extends TestKit(ActorSystem(
  "CartTest"
)) with DefaultTimeout with ImplicitSender with FlatSpecLike with Matchers {

  private val probeCheckout = TestProbe()

  "Cart" should "store items correctly" in {
    import CartManager._
    val probe = TestProbe()
    val customerProbe = TestProbe()
    val cart = system.actorOf(CartManager.props(customerProbe.testActor)())

    val item = Item(URI.create("item1"), "t1", 3.4, 100)
    val item2 = Item(URI.create("item2"), "t2", 44, 10)
    cart ! ItemAdded(item)
    cart ! ItemAdded(item2)
    (cart ! StartCheckout)(probe.testActor)
    probe expectMsg (Cart.empty addItem item addItem item2)

  }

  "Checkout" should "react correctly" in {
    import Checkout._

    val probeCart = TestProbe()
    val probeCustomer = TestProbe()

    val checkout = system.actorOf(
      Checkout.props(
        customer = probeCustomer.testActor)(
        Cart.empty addItem Item(URI.create("simple-item"), "item1", 3.4, 10),
        probeCart.testActor
      )
    )
    checkout ! DeliveryMethod("post-office")
    checkout ! PaymentMethod("pay-pal")
    checkout ! PaymentReceived
    probeCart.expectMsg(CheckoutClosed)
  }


}
