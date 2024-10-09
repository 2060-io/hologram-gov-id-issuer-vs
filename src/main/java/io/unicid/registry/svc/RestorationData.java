package io.unicid.registry.svc;

import io.unicid.registry.enums.RestorationState;
import java.time.LocalDate;

public class RestorationData {
  private RestorationState state;
  private String firstName;
  private String lastName;
  private LocalDate birthDate;

  public RestorationState getState() {
    return state;
  }

  public void setState(RestorationState state) {
    this.state = state;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(LocalDate birthDate) {
    this.birthDate = birthDate;
  }
}
