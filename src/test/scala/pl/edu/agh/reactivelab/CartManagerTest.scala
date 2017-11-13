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

  "Cart manager" should "store items correctly" in {
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

  "Cart manager" should "remove items correctly" in {
    import CartManager._
    val probe = TestProbe()
    val customerProbe = TestProbe()
    val cart = system.actorOf(CartManager.props(customerProbe.testActor)())

    val item = Item(URI.create("item1"), "t1", 3.4, 100)
    val itemDrained = item.copy(count = 90)
    cart ! ItemAdded(item)
    cart ! ItemRemoved(item.copy(count = 10))
    (cart ! StartCheckout)(probe.testActor)
    probe expectMsg (Cart.empty addItem itemDrained)
  }

  "Cart" should "implement buisness logic" in {
    val cart = Cart.empty

    val uri1 = URI.create("item1")
    val uri2 = URI.create("item2")
    val item1 = Item(uri1, "t1", 3.4, 100)
    val item2 = Item(uri2, "t2", 4.5, 100)

    val updated1 = cart addItem item1 addItem item2
    updated1.items should equal(
      Map(
        uri1 -> item1,
        uri2 -> item2
      )
    )

    val updated2 = updated1 addItem item1
    updated2.items should equal(
      Map(
        uri1 -> item1.copy(count = 200),
        uri2 -> item2
      )
    )

    val update3 = updated2.removeItem(uri2, 30)
    update3.items should equal(
      Map(
        uri1 -> item1.copy(count = 200),
        uri2 -> item2.copy(count = 70)
      )
    )

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

  "Checkout" should "restore it's internat state" in {
    import Checkout._

    val probeCart = TestProbe()
    val probeCustomer = TestProbe()

    val checkout1 = system.actorOf(
      Checkout.props(
        customer = probeCustomer.testActor)(
        Cart.empty addItem Item(URI.create("simple-item"), "item1", 3.4, 10),
        probeCart.testActor
      )
    )
    checkout1 ! DeliveryMethod("post-office")
    checkout1 ! "kill"
    Thread.sleep(1000L)
    val checkout2 = system.actorOf(
      Checkout.props(
        customer = probeCustomer.testActor)(
        Cart.empty addItem Item(URI.create("simple-item"), "item1", 3.4, 10),
        probeCart.testActor
      )
    )

    checkout2 ! PaymentMethod("pay-pal")
    checkout2 ! PaymentReceived
    probeCart.expectMsg(CheckoutClosed)

  }



}
