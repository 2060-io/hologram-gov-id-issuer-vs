package io.twentysixty.sa.res.c;

import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.twentysixty.sa.client.res.c.v1.MessageInterface;


@RegisterRestClient
@RegisterClientHeaders
@ClientHeaderParam(name = "X-Delay", value = "{delay}")
public interface MessageResource extends MessageInterface {

    default String delay() {
        try {
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "applied";
    }
}
