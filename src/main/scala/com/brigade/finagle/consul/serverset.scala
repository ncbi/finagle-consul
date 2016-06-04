package com.brigade.finagle.consul

import com.twitter.finagle.{Addr, NameTree, Namer, Resolver, Path, Name}
import com.twitter.util.{Var, Activity}

/**
 * A namer for serverset paths of the form /consul-hosts/consul[KV]:path... where consul-hosts is
 * a consul connect string like 'consul.service.consul:8500'.  Naming is performed by way of a
 * Resolver.
 */
private[consul] trait BaseServersetNamer extends Namer {

  /** Resolve a resolver string to a Var[Addr]. */
  protected[this] def resolve(spec: String): Var[Addr] = Resolver.eval(spec) match {
    case Name.Bound(addr) => addr
    case _ => Var.value(Addr.Neg)
  }

  protected[this] def resolveServerset(strategy: String, hosts: String, path: String) = {
    println(s"$strategy!$hosts!$path")
    resolve(s"$strategy!$hosts!$path")
  }
  /** Bind a name. */
  protected[this] def bind(path: Path): Option[Name.Bound]

  // We have to involve a serverset roundtrip here to return a tree. We run the
  // risk of invalidating an otherwise valid tree when there is a bad serverset
  // on an Alt branch that would never be taken. A potential solution to this
  // conundrum is to introduce some form of lazy evaluation of name trees.
  def lookup(path: Path): Activity[NameTree[Name]] = bind(path) match {
    case Some(name) =>
      // We have to bind the name ourselves in order to know whether
      // it resolves negatively.
      Activity(name.addr map {
        case Addr.Bound(_, _) => Activity.Ok(NameTree.Leaf(name))
        case Addr.Neg => Activity.Ok(NameTree.Neg)
        case Addr.Pending => Activity.Pending
        case Addr.Failed(exc) => Activity.Failed(exc)
      })

    case None => Activity.value(NameTree.Neg)
  }
}

/**
 * The serverset namer takes [[com.twitter.finagle.Path Paths]] of the form
 *
 * {{{
 * hosts/path...
 * }}}
 *
 * and returns a dynamic representation of the resolution of the path into a
 * tree of [[com.twitter.finagle.Name Names]].
 *
 * The namer synthesizes nodes for each endpoint in the serverset.
 * Endpoint names are delimited by the ':' character. For example
 *
 * {{{
 * /$/com.brigade.finagle.consul.serverset/consul/consul.service.consul:8500/my-app:a=b
 * }}}
 *
 * is the endpoint `http` of serverset `/twitter/service/cuckoo/prod/read` on
 * the ensemble `sdzookeeper.local.twitter.com:2181`.
 */
class serverset extends BaseServersetNamer {
  private[this] val idPrefix = Path.Utf8("$", "com.brigade.finagle.consul.serverset")

  protected[this] def bind(path: Path): Option[Name.Bound] = path match {
    case Path.Utf8(strategy, hosts, rest@_*) =>
      val lastStep = rest.last split "\\$"
      val params = lastStep.tail.mkString("?", "&", "").replace(":", "=")
      val name = rest.init :+ lastStep.head + params
      val servicePath = name.mkString("/", "/", "")
      val addr = resolveServerset(strategy, hosts, servicePath)

      // Clients may depend on Name.Bound ids being Paths which resolve
      // back to the same Name.Bound
      val id = idPrefix ++ path
      Some(Name.Bound(addr, id))

    case _ => None
  }

}