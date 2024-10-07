package io.unicid.registry.enums;


public enum IdentityClaim {

	ID("id", "id"),
	CITIZEN_ID("citizenId", "Citizen ID"),
	FIRST_NAME("firstName", "First name"),
	LAST_NAME("lastName", "Last name"),
	AVATAR_NAME("avatarName", "Avatar name"),
	AVATAR_PIC("avatarPic", "Avatar pic"),
	BIRTH_DATE("birthDate", "Birth date"),
	PLACE_OF_BIRTH("placeOfBirth", "Place of Birth"),
	MRZ("mrz", "mrz"),
	GENRE("genre", "Genre"),
	CITIZEN_SINCE("citizenSince", "Citizen Since"),
	PHOTO("photo", "Photo");
	
	private String claimName;
	private String claimLabel;
	
	private IdentityClaim(String claimName, String claimLabel) {
		this.claimName = claimName;
		this.claimLabel = claimLabel;
	}
	
	
	public static IdentityClaim getEnum(String claimName){
		if (claimName == null)
	return null;

		switch(claimName){
		case "id": return ID;
		case "firstName": return FIRST_NAME;
		case "lastName": return LAST_NAME;
		case "avatarName": return AVATAR_NAME;
		case "avatarPic": return AVATAR_PIC;
		case "birthDate": return BIRTH_DATE;
		case "placeOfBirth": return PLACE_OF_BIRTH;
		case "mrz": return MRZ;
		case "sex": return GENRE;
		case "citizenSince": return CITIZEN_SINCE;
		case "photo": return PHOTO;
			
			default: return null;
		}
	}


	public String getClaimName() {
		return claimName;
	}


	public void setClaimName(String claimName) {
		this.claimName = claimName;
	}


	public String getClaimLabel() {
		return claimLabel;
	}


	public void setClaimLabel(String claimLabel) {
		this.claimLabel = claimLabel;
	}
	
	
}
