package gov.nih.nlm.ncbi.finagle.consul.catalog

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import com.twitter.finagle.http.{Method, Request, Response}
import com.twitter.finagle.stats.ClientStatsReceiver
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Addr, Address, Resolver}
import com.twitter.logging.Logger
import com.twitter.util._
import gov.nih.nlm.ncbi.finagle.consul.{ConsulHttpClientFactory, ConsulQuery}
import org.json4s._
import org.json4s.jackson.JsonMethods._

/**
  * A finagle Resolver for services registered in the consul catalog
  */
class ConsulCatalogResolver extends Resolver {

  import ConsulCatalogResolver._

  override val scheme: String = "consul"
  private val log = Logger.get(getClass)
  private val timer = DefaultTimer.twitter
  implicit val format = org.json4s.DefaultFormats

  @volatile
  private var consulIndex: Float = 0

  private val scopedMetrics = ClientStatsReceiver.scope("consul_catalog_resolver")

  // not used by the code, but has to be referenced here so that the gauge doesn't get garbage collected
  private val consulIndexGauge = scopedMetrics.addGauge("consul_index")(consulIndex)

  private val fecthFailureCounter = scopedMetrics.counter("fetch_errors_counter")

  private def datacenterParam(q: ConsulQuery): List[(String, String)] = {
    q.dc
      .map { dc => List("dc" -> dc) }
      .getOrElse(List.empty)
  }

  private def tagParams(q: ConsulQuery): List[(String, String)] = {
    q.tags.toList.map {"tag" -> _}
  }

  private def mkPath(q: ConsulQuery, idx: String) = {
    val path = s"/v1/health/service/${q.name}"
    val params = List(datacenterParam(q), tagParams(q)).flatten :+ ("passing", "true") :+ ("index", idx) :+ ("wait", "10s")
    val query = Request.queryString(params: _*)
    s"$path$query"
  }

  private def jsonToAddresses(json: JValue): Set[InetSocketAddress] = {
    json
      .extract[Set[HealthJson]]
      .map { ex => new InetSocketAddress(Option(ex.Service.Address).filterNot(_.isEmpty).getOrElse(ex.Node.Address), ex.Service.Port) }
  }


  private def fetch(hosts: String, q: ConsulQuery, idx: String): Future[Response] = {
    val client = ConsulHttpClientFactory.getClient(hosts)
    val path = mkPath(q, idx)
    val req = Request(Method.Get, path)
    req.host = "localhost"

    log.debug(s"Executing GET $req")

    client(req)
  }

  private def updateConsulIndex(index: String) =
    Try(index.toFloat).foreach(index => consulIndex = index)

  def addrOf(hosts: String, query: ConsulQuery): Var[Addr] = Var.async(Addr.Pending: Addr) { update =>
    @volatile var running = true

    def cycle(index: String): Future[Unit] =
      if (running) {
        updateConsulIndex(index)

        fetch(hosts, query, index) transform {
          case Return(response) =>
            val as = jsonToAddresses(parse(response.getContentString()))
            update() = Addr.Bound(as.map(Address(_)))
            val idx = response.headerMap.getOrElse("X-Consul-Index", "0")

            cycle(idx)
          case Throw(t) =>
            log.warning(t, s"Exception throw while querying Consul for service discovery")
            fecthFailureCounter.incr()

            timer.doLater(Duration(1, TimeUnit.SECONDS)) {
              cycle(index)
            }
        }
      } else Future.Done

    cycle("0")

    Closable make { _ => running = false; Future.Done }
  }

  override def bind(arg: String): Var[Addr] = arg.split("!") match {
    case Array(hosts, query) =>
      ConsulQuery.decodeString(query) match {
        case Some(q) => addrOf(hosts, q)
        case None => throw new IllegalArgumentException(s"Invalid address '$arg'")
      }
    case _ => throw new IllegalArgumentException(s"Invalid address '$arg'")
  }
}

object ConsulCatalogResolver {
  // These case classes are used to match the "Service" objects in the docs below
  // The consul json API is not consistent, so we can't just reuse other "Service" case classes:
  // https://www.consul.io/docs/agent/http/health.html#health_service
  case class HealthJson(Node: NodeHealthJson, Service: ServiceHealthJson)

  case class ServiceHealthJson(ID: Option[String],
                               Service: String,
                               Address: String,
                               Tags: Option[Seq[String]],
                               Port: Int)

  case class NodeHealthJson(Address: String)
}
