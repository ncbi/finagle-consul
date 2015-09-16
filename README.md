## Finagle Consul

Service discovery for Finagle cluster with Consul. This project originaly
developed by
[kachayev/finagle-consul](https://github.com/kachayev/finagle-consul), Unlike kachayev’s version, where services are used, here we use sessions and k/v for discovery.

### About
[Consul](https://www.consul.io/) is a distributed, highly available and
extremely scalable tool for service discovery and configuration.

This project is using Consul sessions and K/V storage for announces. Unlike
Consul services, sessions allow to set up key TTL's, and when application is
killed by OOM killer or closed unexpectedly, the session and keys, associated
with it, are automatically removed after TTL is expired.

Service definitions are stored in `/v1/kv/finagle/services/:name/:sessionId`,
you can specify a name as URL, but all "/" will be replaced with "."

### Install

**Warning! This is still BETA.**

Add the following to your sbt build.sbt file:

```scala
resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.github.dmexe" %% "finagle-consul" % "0.0.1"
)
```

### Consul path definition

To announce your service use the following scheme:

```
consul!host1:port1,host2:port2,...!serviceName
```

For example,

```scala
val server = Http.serveAndAnnounce("consul!127.0.0.1:8500!/RandomNumber")
val client = Http.newService("consul!127.0.0.1:8500!/RandomNumber")
```

### TODO

- [ ] Discovery for native Consul services
- [ ] Support tags and datacenters
- [ ] Leader election top of Consul API
- [ ] Handle Consul API timeouts

### Known issues

...

