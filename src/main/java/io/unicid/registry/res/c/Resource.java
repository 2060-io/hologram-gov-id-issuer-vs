package io.unicid.registry.res.c;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;
import java.io.InputStream;
import org.jboss.resteasy.annotations.providers.multipart.PartType;

public class Resource {

  @FormParam("chunk")
  @PartType(MediaType.APPLICATION_OCTET_STREAM)
  public InputStream chunk;
}
