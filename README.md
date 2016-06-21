## Finagle Consul

Service discovery for Finagle cluster with Consul. This project was originally
developed by
[kachayev/finagle-consul](https://github.com/kachayev/finagle-consul) and [dmexe/finagle-consul](https://github.com/dmexe/finagle-consul)

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


#### K/V strategy

Usage (a Consul Agent is never required):

```
consulKV!host1:port1,host2:port2,...!/serviceName
```

For Example:

```scala
val server = Http.serveAndAnnounce("consulKV!127.0.0.1:8500!/RandomNumber")
val client = Http.newService("consulKV!127.0.0.1:8500!/RandomNumber")
```

Pros:
* Services are completely removed from Consul when they fail their TTL check
* Doesn't require a Consul Agent to announce services

Cons:
* TTLs can't be under 10s (a limitation of Consul sessions)
* Doesn't integrate with native Consul Services

The K/V strategy replicates Zookeeper-like service discovery using ephemeral
key value pairs. If Consul is only being used for service discovery between
finagle services, this is the way to go.

Unlike the catalog, Consul services and sessions support TTLs for keys, so, when
an application killed by OOM killer or closed unexpectedly, the session and
keys associated with it are automatically removed after the TTL expires.

The major drawback of this approach is that non-Finagle services will not be
able to discover or be discovered by this mechanism without custom integration.

Service definitions are stored in `/v1/kv/finagle/services/:name/:sessionId`,
you can specify a name as URL, but all "/" will be replaced with "."


### Example

#### Server

```
val server = Http.server.serveAndAnnounce("consul!localhost:8500!/EchoServer?ttl=5", s":$serverPort", service)

// Ensure clean de-registration 
sys.addShutdownHook {
  Await.result(server.close())
}
Await.ready(server)
```

#### Client

```
val client = Http.client.newService("consul!localhost:8500!/EchoServer?ttl=2")

```

#### D-Tab

```
Dentry.read("/account=>/$/com.brigade.finagle.consul.serverset/consul/consul.service.aws-prod.consul:80/account-version-2-2-build-73")))
```
