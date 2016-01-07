package com.brigade.finagle.consul.kv

import com.brigade.finagle.consul.{ConsulHttpClientFactory, ConsulQuery, ConsulSession}
import com.twitter.finagle.{Announcement, Announcer}
import com.twitter.util.Future

import java.net.InetSocketAddress

/**
 * A finagle Announcer that uses Consul's KV and session functionality
 */
class ConsulKVAnnouncer extends Announcer {

  import ConsulKVAnnouncer._

  val scheme = "consulKV"

  def announce(ia: InetSocketAddress, hosts: String, q: ConsulQuery): Future[Announcement] = {
    val address  = ia.getAddress.getHostAddress
    val session  = ConsulSession.get(hosts)
    val service  = new ConsulKVClient(ConsulHttpClientFactory.getClient(hosts))
    val listener = new SessionListener(service, q.name, address, ia.getPort, q.tags)

    session.addListener(listener)
    session.start()

    Future {
      new Announcement {
        override def unannounce() = Future[Unit] {
          session.delListener(listener)
          session.stop()
        }
      }
    }
  }

  override def announce(ia: InetSocketAddress, addr: String): Future[Announcement] = {
    addr.split("!") match {
      case Array(hosts, query) =>
        ConsulQuery.decodeString(query) match {
          case Some(q) => announce(ia, hosts, q)
          case None =>
            val exc = new IllegalArgumentException(s"Invalid addr '$addr'")
            Future.exception(exc)
        }
      case _ =>
        val exc = new IllegalArgumentException(s"Invalid addr '$addr'")
        Future.exception(exc)
    }
  }
}

object ConsulKVAnnouncer {
  class SessionListener(service: ConsulKVClient, name: String, address: String, port: Int, tags: Set[String])
    extends ConsulSession.Listener {

    def start(session: String): Unit = {
      val newSrv = ConsulKVClient.ServiceJson(session, name, address, port, tags)
      service.create(newSrv)
    }

    def stop(session: String): Unit = {
      service.destroy(session, name)
    }
  }
}
