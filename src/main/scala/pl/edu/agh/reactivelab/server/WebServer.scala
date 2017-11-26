package pl.edu.agh.reactivelab.server

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object WebServer {
  def main(args: Array[String]) {

    implicit val system: ActorSystem = ActorSystem("my-system")

    implicit val materializer: ActorMaterializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val customers = TrieMap.empty[String, ActorRef]

    def handlePayment(
      initMsg: Int => String,
      confirmMsg: String => String,
    ) = {
      path("initialize") {
        (get & parameters('amount.as[Int])) { amount =>
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, initMsg(amount)))
        }
      } ~ path("process") {
        (get & parameters('order.as[String])) { msg =>
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, confirmMsg(msg)))
        }
      }
    }

    val route =
      path("payment") {
        path("visa") {
          handlePayment(d => s"[VISA: order: $d]", d => s"[VISA: $d - completed]")
        } ~ handlePayment(d => s"[MOCK: order: $d]", d => s"[MOCK: $d - completed]")
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}