package com.brigade.finagle.consul.kv

import com.brigade.finagle.consul.ConsulHttpClientFactory
import com.brigade.finagle.consul.client.KeyService
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service => HttpService}
import com.twitter.util.Await

import java.util.logging.Logger

import scala.collection.mutable

class ConsulKVService(httpClient: HttpService[Request, Response]) {

  import ConsulKVService._

  private val log    = Logger.getLogger(getClass.getName)
  private val client = KeyService(httpClient)

  def list(name: String): List[Service] = {
    val reply = Await.result(client.getJsonSet[Service](lockName(name)))
    reply.map(_.Value).toList
  }

  private[consul] def create(service: Service): Unit = {
    val reply = client.acquireJson[Service](lockName(service.ID, service.Service), service, service.ID)
    Await.result(reply)
    log.info(s"Consul service registered name=${service.Service} session=${service.ID} addr=${service.Address}:${service.Port}")
  }

  private[consul] def destroy(session: String, name: String): Unit = {
    val reply = client.delete(lockName(session, name))
    Await.result(reply)
    log.info(s"Consul service deregistered name=$name session=$session")
  }

  private def lockName(name: String): String = {
    s"finagle/services/$name"
  }

  private def lockName(session: String, name: String): String = {
    lockName(name) + s"/$session"
  }
}

object ConsulKVService {
  case class Service(ID: String, Service: String, Address: String, Port: Int, Tags: Set[String], dc: Option[String] = None)

  private val services: mutable.Map[String, ConsulKVService] = mutable.Map()

  def get(hosts: String): ConsulKVService = {
    synchronized {
      val service = services.getOrElseUpdate(hosts, {
        val newClient = ConsulHttpClientFactory.getClient(hosts)
        new ConsulKVService(newClient)
      })
      service
    }
  }
}
