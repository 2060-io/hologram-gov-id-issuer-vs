package io.unicid.registry.enums;

public enum EventNotificationType {
        PEER_JOINED("peer-joined"),
        PEER_LEFT("peer-left"),
	
	
        ;
        
        private String typeName;
        
        private EventNotificationType(String typeName) {
            this.typeName = typeName;
            
        }
    
        public String getTypeName() {
            return typeName;
        }
    
        public void setTypeName(String typeName) {
            this.typeName = typeName;
        }
        
    }
