package io.twentysixty.sa.res.c;

import io.twentysixty.sa.client.res.c.v1.MessageInterface;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient
@RegisterClientHeaders
@ClientHeaderParam(name = "X-Delay", value = "{delay}")
public interface MessageResource extends MessageInterface {

  default String delay() {
    try {
      TimeUnit.SECONDS.sleep(1);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return "applied";
  }
}
