package com.brigade.finagle.consul.catalog

import com.brigade.finagle.consul.{ConsulQuery, ConsulHttpClientFactory}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Announcement, Announcer}
import com.twitter.logging.Logger
import com.twitter.util.{Await, Duration, Future}

import java.net.InetSocketAddress

import java.util.concurrent.TimeUnit

/**
 * An !!!Experimental!!! announcer to register services in Consul's service catalog via a consul agent
 *
 * Note: a consul agent is required for health checks.
 *
 * WARNING: this announcer is severely limited by: https://github.com/hashicorp/consul/issues/679
 * Until the issue is resolved, service definitions are not ephemeral, so an external process is required
 * to clean them up.
 * This is not an issue if the server is shutdown cleanly (unannounce is called).
 *
 * @see [[https://consul.io/docs/agent/services.html]]
 */
class ConsulAgentCatalogAnnouncer extends Announcer {
  override val scheme: String = "consul"
  private val timer = DefaultTimer.twitter
  private val log = Logger.get(getClass)
  val maxHeartbeatFrequency = Duration(10, TimeUnit.SECONDS)

  def announce(ia: InetSocketAddress, hosts: String, q: ConsulQuery): Future[Announcement] = {
    val consulClient = new ConsulAgentClient(ConsulHttpClientFactory.getClient(hosts))
    val registrationFuture = consulClient.register(ia, q)

    registrationFuture
      .map { regResponse =>
        log.debug(s"Successfully registered consul service: $regResponse")

        consulClient.healthCheck(regResponse.checkId) // initial healthcheck

        // start the healthcheck and always make it less than the TTL
        val freq = q.ttl / 2
        require(freq.inSeconds >= 1, "Service TTL must be above two seconds!")
        val heartbeatFrequency = freq.min(maxHeartbeatFrequency)
        log.debug(s"Heartbeat frequency: $heartbeatFrequency")
        val heartbeatTask = timer.schedule(heartbeatFrequency) {
          log.trace("heartbeat")
          Await.result(consulClient.healthCheck(regResponse.checkId))
        }

        new Announcement {
          override def unannounce(): Future[Unit] = {
            // sequence stopping the heartbeat and deleting the service registration
            for {
              _ <- heartbeatTask.close()
              _ <- consulClient.deregisterService(regResponse.serviceId)
            } yield {}
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
