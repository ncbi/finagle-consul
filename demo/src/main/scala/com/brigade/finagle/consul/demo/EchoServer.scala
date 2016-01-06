package com.brigade.finagle.consul.demo

import com.twitter.finagle.http.{Status, Request, Response}
import com.twitter.finagle.{Service, Http}
import com.twitter.util.{Await, Future}

/**
 *
 */
object EchoServer {
  def main(args: Array[String]): Unit = {
    val serverPort = args.headOption.getOrElse("8080")
    val service = new Service[Request, Response] {
      override def apply(request: Request): Future[Response] = {
        println(request.getContentString())
        Future.value {
          val resp = Response(Status.Ok)
          resp.setContentString(serverPort + "  " + request.getContentString())
          resp
        }
      }
    }

    val server = Http.server.serveAndAnnounce("consulKV!localhost:8500!/EchoServer", s":$serverPort", service)
    Await.ready(server)
  }
}
