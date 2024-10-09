package io.unicid.registry.svc;

import io.unicid.registry.enums.RestoreStep;

public class RestoreSessionData extends SessionData {

  private RestoreStep restoreStep;

  public RestoreStep getRestoreStep() {
    return restoreStep;
  }

  public void setRestoreStep(RestoreStep restoreStep) {
    this.restoreStep = restoreStep;
  }
}
