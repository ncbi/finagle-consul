## Finagle Consul

Service discovery for Finagle cluster with Consul. This project was originally
developed by
[kachayev/finagle-consul](https://github.com/kachayev/finagle-consul), [dmexe/finagle-consul](https://github.com/dmexe/finagle-consul), [matteobanerjee/finagle-consul](https://github.com/matteobanerjee/finagle-consul) and [ncbi/finagle-consul](https://github.com/ncbi/finagle-consul). 

### About
[Consul](https://www.consul.io/) is a distributed, highly available and
extremely scalable tool for service discovery and configuration.

This project provides two resolution/announcement strategies: one using
Consul sessions and K/V, the other using Consul's native service registration
mechanism (referred to as the "catalog" strategy here).

#### Catalog strategy

Usage:

Announce (the announcer requires an agent, usually running on localhost):

```
consul!localhost:8500!/serviceName
```

Resolve (doesn't require an agent):

```
consul!host1:port1,host2:port2,...!/serviceName
```

For Example:

```scala
val server = Http.serveAndAnnounce("consul!127.0.0.1:8500!/RandomNumber")
val client = Http.newService("consul!127.0.0.1:8500!/RandomNumber")
```

Pros:
* Integrates cleanly with native Consul Services

Cons:
* Requires a Consul Agent for server announcements
* Requires clean up of dead services in environments where server addresses are
ephemeral, like Mesos

The catalog strategy uses Consul's built-in service discovery APIs. The main
advantage of this is that it makes it easy to run Finagle services alongside
non-finagle services. You can, for example, discover a Rails web service and
MySQL database using this resolver.

The main downside is on the announcement side: The consul catalog does not
support ephemeral nodes, so services will not be deregistered in case of an
unclean shutdown, e.g. OOM errors or `kill -9`. They will instead only fail
their health check and be put in a critical state. Though the resolver will
filter these out, the Consul catalog will get polluted with dead nodes and will
require clean up if running in an environment like Mesos.
This is an issue with [Consul itself](https://github.com/hashicorp/consul/issues/679)
that can be mitigated with a cron job.



### Example

#### Server

```
val server = Http.server.serveAndAnnounce("consul!localhost:8500!/EchoServer?ttl=100", s":$serverPort", service)

// Ensure clean de-registration
sys.addShutdownHook {
  Await.result(server.close())
}
Await.ready(server)
```

#### Client

```
val client = Http.client.newService("consul!localhost:8500!/EchoServer")

```

#### D-Tab

```
Dentry.read("/account=>/$/gov.nih.nlm.ncbi.finagle.serverset/consul/consul.service.aws-prod.consul:80/account-version-2-2-build-73")))
```
