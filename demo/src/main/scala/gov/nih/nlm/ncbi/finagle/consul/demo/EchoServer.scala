package gov.nih.nlm.ncbi.finagle.consul.demo

import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.{Http, Service}
import com.twitter.util.{Await, Future}


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

    val server = Http.server.serveAndAnnounce("consul!localhost:8500!/EchoServer?ttl=5", s":$serverPort", service)
    sys.addShutdownHook {
      Await.result(server.close())
    }
    Await.ready(server)
  }
}
