package com.brigade.finagle.consul.catalog

import com.twitter.finagle.http.{Method, Request, Response, Status}
import com.twitter.finagle.{Http, ListeningServer, Service}
import com.twitter.util.{Await, Future}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.util.Random

class CatalogE2ESpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  val svcName = Random.alphanumeric.take(10).mkString

  s"servers and client communication using the Consul Catalog [$svcName]" should {
    "work" in {


      val service0 = new Service[Request, Response] {
        def apply(req: Request) = Future.value(Response(req.version, Status.Ok))
      }

      var server0: ListeningServer = null
      var server1: ListeningServer = null
      var server2: ListeningServer = null
      var server3: ListeningServer = null
      var client: Service[Request, Response] = null

      try {
        val success = Status(200)
        server0 = Http.serveAndAnnounce(s"consul!localhost:8500!/$svcName", ":8880", service0)
        server1 = Http.serveAndAnnounce(s"consul!localhost:8500!/$svcName", ":8881", service0)
        Thread.sleep(1000)

        client = Http.newService(s"consul!localhost:8500!/$svcName")
        val req = Request(Method.Get, "/")
        req.host = "localhost"

        // live: 0,1
        Await.result(client(req)).status should be(success)

        // live 1
        server0.close()
        Thread.sleep(1000)

        server2 = Http.serveAndAnnounce(s"consul!localhost:8500!/$svcName", ":8882", service0)
        Thread.sleep(1000)

        // live 0,2
        Await.result(client(req)).status should be(success)
        // live 2
        server1.close()

        Thread.sleep(1000)
        server3 = Http.serveAndAnnounce(s"consul!localhost:8500!/$svcName", ":8883", service0)
        Thread.sleep(1000)

        // live 2,3
        Await.result(client(req)).status should be(success)
      } finally {
        if (server0 != null) sys.addShutdownHook { Await.result(server0.close()) }
        if (server1 != null) sys.addShutdownHook { Await.result(server1.close()) }
        if (server2 != null) sys.addShutdownHook { Await.result(server2.close()) }
        if (server3 != null) sys.addShutdownHook { Await.result(server3.close()) }
      }
    }
  }
}
