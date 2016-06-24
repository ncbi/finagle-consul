package gov.nih.nlm.ncbi.finagle.consul.catalog

sealed trait ConsulCheck

case class TtlCheck(TTL: String) extends ConsulCheck
