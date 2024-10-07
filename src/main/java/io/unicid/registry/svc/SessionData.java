package io.unicid.registry.svc;

import java.time.LocalDate;
import java.util.UUID;


public class SessionData {
	
	
	private String firstName;
	private String lastName;
	private LocalDate birthDate;
	private String placeOfBirth;

	private UUID identityId;

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

	

	public String getPlaceOfBirth() {
		return placeOfBirth;
	}

	public void setPlaceOfBirth(String placeOfBirth) {
		this.placeOfBirth = placeOfBirth;
	}

	public UUID getIdentityId() {
		return identityId;
	}

	public void setIdentityId(UUID identityId) {
		this.identityId = identityId;
	}
	
	
}
