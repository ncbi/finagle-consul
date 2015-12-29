package com.github.dmexe.finagle.consul

import com.twitter.finagle.http.Response

object ConsulErrors {
  class BadResponseException(msg: String) extends RuntimeException(msg)

  private[consul] def badResponse(reply: Response) = {
    new BadResponseException(s"code=${reply.getStatusCode()} body=${reply.contentString}")
  }
}
