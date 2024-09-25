package io.unicid.registry.enums;

public enum PeerType {
        PEER_USER("user"),
        PEER_VISION("vision"),
	
	
        ;
        
        private String typeName;
        
        private PeerType(String typeName) {
            this.typeName = typeName;
            
        }
    
        public String getTypeName() {
            return typeName;
        }
    
        public void setTypeName(String typeName) {
            this.typeName = typeName;
        }
        
    }
