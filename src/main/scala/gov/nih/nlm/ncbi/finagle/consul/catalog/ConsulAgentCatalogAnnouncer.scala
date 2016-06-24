package gov.nih.nlm.ncbi.finagle.consul.catalog

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Announcement, Announcer}
import com.twitter.logging.Logger
import com.twitter.util.{Await, Duration, Future, FutureCancelledException}
import gov.nih.nlm.ncbi.finagle.consul.{ConsulHttpClientFactory, ConsulQuery}

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

    // start the hearbeats and always make it less than the TTL
    require(q.ttl.inMilliseconds >= 50, "Service TTL must be above 50 ms!")
    val freq = q.ttl / 2
    val heartbeatFrequency = freq.min(maxHeartbeatFrequency)
    log.debug(s"Heartbeat frequency: $heartbeatFrequency")

    registrationFuture
      .map { regResponse =>
        log.debug(s"Successfully registered consul service: $regResponse")

        consulClient.sendHearbeat(regResponse.checkId) // initial heartbeat

        val heartbeatTask = timer.schedule(heartbeatFrequency) {
          log.trace("heartbeat")
          heartbeatFuture = consulClient.sendHearbeat(regResponse.checkId)
        }

        new Announcement {
          override def unannounce() = {
            running = false
            heartbeatTask.close()
            Await.ready(heartbeatFuture)
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
