package com.brigade.finagle.consul.catalog

import com.brigade.finagle.consul.{ConsulQuery, ConsulHttpClientFactory}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Announcement, Announcer}
import com.twitter.logging.Logger
import com.twitter.util.{FutureCancelledException, Await, Duration, Future}

import java.net.InetSocketAddress

import java.util.concurrent.TimeUnit

/**
 * Note: a consul agent is required for health checks.
 *
 * @see [[https://consul.io/docs/agent/services.html]]
 */
class ConsulAgentCatalogAnnouncer extends Announcer {
  override val scheme: String = "consul"
  private val timer = DefaultTimer.twitter
  private val log = Logger.get(getClass)
  val maxHeartbeatFrequency = Duration(10, TimeUnit.SECONDS)

  def announce(ia: InetSocketAddress, hosts: String, q: ConsulQuery): Future[Announcement] = {
    @volatile var running = true
    @volatile var heartbeatFuture: Future[Unit] = Future.Done

    val consulClient = new ConsulAgentClient(ConsulHttpClientFactory.getClient(hosts))
    val registrationFuture = consulClient.register(ia, q)

    registrationFuture
      .map { regResponse =>
        log.debug(s"Successfully registered consul service: $regResponse")

        consulClient.sendHearbeat(regResponse.checkId) // initial heartbeat

        // start the hearbeats and always make it less than the TTL
        val freq = q.ttl / 2
        require(freq.inMilliseconds >= 10, "Service TTL must be above 10 ms!")
        val heartbeatFrequency = freq.min(maxHeartbeatFrequency)
        log.debug(s"Heartbeat frequency: $heartbeatFrequency")
        val heartbeatTask = timer.schedule(heartbeatFrequency) {
          log.trace("heartbeat")
          heartbeatFuture = consulClient.sendHearbeat(regResponse.checkId)
        }

        new Announcement {
          override def unannounce() = {
            running = false
            heartbeatTask.close()
            heartbeatFuture.raise(new FutureCancelledException)
            consulClient.deregisterService(regResponse.serviceId)
          }
      }
    }
  }

  override def announce(ia: InetSocketAddress, addr: String): Future[Announcement] = addr.split("!") match {
    case Array(hosts, query) =>
      ConsulQuery.decodeString(query) match {
        case Some(q) => announce(ia, hosts, q)
        case None => Future.exception(new IllegalArgumentException(s"Invalid addr '$addr'"))
      }
    case _ => Future.exception(new IllegalArgumentException(s"Invalid addr '$addr'"))
  }
}
