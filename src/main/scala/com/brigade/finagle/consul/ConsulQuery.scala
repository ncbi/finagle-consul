package com.brigade.finagle.consul

import com.twitter.util.Duration
import org.jboss.netty.handler.codec.http.QueryStringDecoder

import scala.collection.JavaConverters._

case class ConsulQuery(
  name: String,
  ttl:  Duration,
  tags: Set[String],
  dc:   Option[String]
)

object ConsulQuery {

  def readTTL(ttls: java.util.List[String]): Duration = Duration.fromMilliseconds(ttls.asScala.head.toLong)

  def decodeString(query: String): Option[ConsulQuery] = {
    val q      = new QueryStringDecoder(query)
    val name   = q.getPath.stripPrefix("/").split("/") mkString "_"
    val params = q.getParameters.asScala
    val ttl    = params.get("ttl").map(readTTL).getOrElse(Duration.fromMilliseconds(100))
    val tags   = params.get("tag").map(_.asScala.toSet).getOrElse(Set.empty[String])
    val dc     = params.get("dc").map(_.get(0))
    Some(ConsulQuery(name, ttl, tags, dc))
  }
}
