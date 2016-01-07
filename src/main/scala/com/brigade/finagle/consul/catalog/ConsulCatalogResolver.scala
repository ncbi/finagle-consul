package com.brigade.finagle.consul.catalog

import com.brigade.finagle.consul.{ConsulHttpClientFactory, ConsulQuery}
import com.twitter.finagle.http.{Method, RequestBuilder, Request}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Addr, Resolver}
import com.twitter.logging.Logger
import com.twitter.util.{Await, Var}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import java.net.{SocketAddress, InetSocketAddress}

/**
 * A finagle Resolver for services registered in the consul catalog
 */
class ConsulCatalogResolver extends Resolver {
  import ConsulCatalogResolver._

  override val scheme: String = "consul"
  private val log = Logger.get(getClass)
  private val timer = DefaultTimer.twitter
  implicit val format = org.json4s.DefaultFormats

  private def datacenterParam(q: ConsulQuery): List[(String, String)] = {
    q.dc
      .map { dc => List("dc" -> dc) }
      .getOrElse(List.empty)
  }

  private def tagParams(q: ConsulQuery): List[(String, String)] = {
    q.tags.toList.map { "tag" -> _ }
  }

  private def catalogPath(q: ConsulQuery) = {
    val path = s"/v1/catalog/service/${q.name}"
    val params = List(datacenterParam(q), tagParams(q)).flatten
    val query = Request.queryString(params: _*)
    s"$path$query"
  }

  private def jsonToAddress(location: ServiceLocationJson): SocketAddress = {
    val address =
      if (location.ServiceAddress.isEmpty) {
        location.Address
      } else {
        location.ServiceAddress
      }

    new InetSocketAddress(address, location.ServicePort)
  }

  private def addresses(hosts: String, q: ConsulQuery) : Set[SocketAddress] = {
    val client = ConsulHttpClientFactory.getClient(hosts)
    val path = catalogPath(q)
    val req = Request(Method.Get, path)

    val f = client(req).map { resp =>
      val as = parse(resp.getContentString()).extract[Set[ServiceLocationJson]].map(jsonToAddress)

      log.debug(s"Consul catalog lookup at hosts:$hosts path:$path addresses: $as")
      as
    }

    Await.result(f)
  }

  def addrOf(hosts: String, query: ConsulQuery): Var[Addr] = {
    Var.async(Addr.Pending: Addr) { u =>
      u() = Addr.Bound(addresses(hosts, query))

      timer.schedule(query.ttl) {
        val addrs = addresses(hosts, query)
        if (addrs.nonEmpty) u() = Addr.Bound(addresses(hosts, query))
      }
    }
  }

  override def bind(arg: String): Var[Addr] = {
    arg.split("!") match {
      case Array(hosts, query) =>
        ConsulQuery.decodeString(query) match {
          case Some(q) => addrOf(hosts, q)
          case None =>
            throw new IllegalArgumentException(s"Invalid address '$arg'")
        }

      case _ =>
        throw new IllegalArgumentException(s"Invalid address '$arg'")
    }
  }
}

object ConsulCatalogResolver {
  case class ServiceLocationJson(
    Node: String,
    Address: String,
    ServiceID: String,
    ServiceName: String,
    ServiceTags: List[String],
    ServiceAddress: String,
    ServicePort: Int
  )
}
