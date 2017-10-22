package pl.edu.agh.reactivelab

import akka.actor.ActorSystem

import scala.concurrent.Await
import scala.util.Random
import scala.concurrent.duration._
import akka.pattern._
import akka.util.Timeout
import pl.edu.agh.reactivelab.Cart.CheckoutStartedResponse

object Application {

  def main(args: Array[String]): Unit = {
    //simulate successful shopping
    val random = new Random()
    val items = random shuffle "shoes" :: "socks" :: "trousers" :: "shirt" :: "dress" :: Nil
    val system = ActorSystem()
    import system._
    val cart = system.actorOf(Cart.props)

    def cycleApplication(retry: Int = 2): Unit = {
      val lastAdded = (items foldLeft 0.millis) { (prev, item) =>
        val next = prev + 1.second
        scheduler.scheduleOnce(next, cart, Cart.ItemAdded(item))
        next
      }

      val lastRemoved = (items drop 3 foldLeft lastAdded) { (prev, item) =>
        val next = prev + 2.seconds
        scheduler.scheduleOnce(next, cart, Cart.ItemRemoved(item))
        next
      }

      implicit val timeout = Timeout(10.second)

      scheduler.scheduleOnce(lastRemoved + 1.second) {
        for (CheckoutStartedResponse(checkout) <- (cart ? Cart.CheckoutStarted).mapTo[CheckoutStartedResponse]) {
          scheduler.scheduleOnce(1.second, checkout, Checkout.DeliveryMethod("postman"))
          scheduler.scheduleOnce(2.second, checkout, Checkout.PaymentMethod("pay-pal"))
          scheduler.scheduleOnce(3.second, checkout, Checkout.PaymentReceived)
          if (retry > 0) {
            scheduler.scheduleOnce(4.second)(cycleApplication(retry - 1))
          }
        }
      }
    }

    cycleApplication()

    io.StdIn.readLine("Press any key to exit")
    Await.ready(system.terminate(), Duration.Inf)
  }

}
