package io.unicid.registry.svc;

import io.unicid.registry.enums.EditStep;

public class EditSessionData extends SessionData {

  private EditStep editStep;

  public EditStep getEditStep() {
    return editStep;
  }

  public void setEditStep(EditStep editStep) {
    this.editStep = editStep;
  }
}
