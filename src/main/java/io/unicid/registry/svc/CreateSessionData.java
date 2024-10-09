package io.unicid.registry.svc;

import io.unicid.registry.enums.CreateStep;

public class CreateSessionData extends SessionData {

  private CreateStep createStep;

  public CreateStep getCreateStep() {
    return createStep;
  }

  public void setCreateStep(CreateStep createStep) {
    this.createStep = createStep;
  }
}
