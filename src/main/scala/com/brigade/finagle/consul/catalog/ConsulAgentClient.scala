package com.brigade.finagle.consul.catalog

import com.brigade.finagle.consul.ConsulQuery
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Method, Request, Response}
import com.twitter.logging.Logger
import com.twitter.util.{Duration, Future}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * A wrapper around Consul agent service registration HTTP calls
 */
class ConsulAgentClient(httpClient: Service[Request, Response]) {

  import ConsulAgentClient._

  private lazy val log = Logger.get(getClass)
  implicit val formats = DefaultFormats

  def healthCheckPath(checkId: String): String = s"/v1/agent/check/pass/$checkId"

  def registerPath: String = "/v1/agent/service/register"

  def deregisterPath(serviceId: String): String = s"/v1/agent/service/deregister/$serviceId"

  def formatTtl(d: Duration): String = s"${d.inUnit(TimeUnit.SECONDS)}s"

  def register(ia: InetSocketAddress, q: ConsulQuery): Future[RegisterResponse] = {
    val address = ia.getAddress.getHostAddress
    val port = ia.getPort

    // use the service name + the address to allow multiple instances on the same host
    val serviceId = s"${q.name}-$address-$port"

    // See: https://consul.io/docs/agent/services.html, the check id is set to service:service-id
    val checkId = s"service:$serviceId"

    val check = TtlCheck(TTL = formatTtl(q.ttl))
    val serviceDefJson = ConsulServiceJson(
      ID = Some(serviceId),
      Name = q.name,
      Tags = q.tags,
      Address = Some(address),
      Port = Some(port),
      Check = Some(check)
    )

    val request = Request(Method.Put, registerPath)
    request.setContentTypeJson()
    request.setContentString(Serialization.write(serviceDefJson))

    httpClient(request).map { response =>
      val status = response.getStatusCode()
      if (status != 200) {
        val msg = response.getContentString()
        // fail the future
        throw new RuntimeException(s"Registration failed status=$status message=$msg")
      } else {
        println(response.getContentString())
        RegisterResponse(serviceId, checkId)
      }
    }
  }

  // TODO: DRY these up
  def deregisterService(serviceId: String): Future[Unit] = {
    val request = Request(Method.Put, deregisterPath(serviceId))
    httpClient(request).map { response =>
      val status = response.getStatusCode()
      if (status != 200) {
        val msg = response.getContentString()
        // fail the future
        throw new RuntimeException(s"Deregister service failed status=$status message=$msg")
      } else {
        ()
      }
    }
  }

  def healthCheck(checkId: String): Future[Unit] = {
    // https://consul.io/docs/agent/http/agent.html#agent_check_pass
    val request = Request(Method.Get, healthCheckPath(checkId))

      httpClient(request).map { response =>
        val status = response.getStatusCode()
        if (status != 200) {
          val msg = response.getContentString()
          throw new RuntimeException(s"Health check failed status=$status message=$msg")
        } else {
          ()
        }
      }
  }
}

object ConsulAgentClient {
  case class RegisterResponse(serviceId: String, checkId: String)
}
