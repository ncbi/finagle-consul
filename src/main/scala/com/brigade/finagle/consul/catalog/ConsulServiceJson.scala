package com.brigade.finagle.consul.catalog

// Keys match consul's JSON format
// https://consul.io/docs/agent/services.html
case class ConsulServiceJson(
  ID: Option[String],
  Name: String,
  Tags: Set[String],
  Address: Option[String],
  Port: Option[Int],
  Check: Option[ConsulCheck]
)
