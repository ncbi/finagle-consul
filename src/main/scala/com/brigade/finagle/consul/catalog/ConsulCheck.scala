package com.brigade.finagle.consul.catalog

sealed trait ConsulCheck

case class TtlCheck(TTL: String) extends ConsulCheck
