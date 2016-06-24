package gov.nih.nlm.ncbi.finagle.consul.demo

import com.twitter.finagle.Http
import com.twitter.finagle.http.Request
import com.twitter.util.Await


object EchoPinger {
  def main(args: Array[String]): Unit = {
    val cli = Http.client.newService("consul!localhost:8500!/EchoServer?ttl=2")
    (1 to 100).foreach { v =>
      Thread.sleep(1000)
      val req = Request()
      req.setContentString(v.toString)
      println(Await.result(cli(req)).getContentString())
    }
  }
}
