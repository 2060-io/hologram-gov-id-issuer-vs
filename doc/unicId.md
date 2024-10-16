# UnicId API

This document outlines the user interface provided by UnicId for its integration with various services that either depend on it or are required for its proper functionality.

UnicId API consists on a REST-like interface that exposes endpoints to:

WebRTC Service
- Handle service events by logging connections and creating rooms for new connections
- Listen for the service's response to potential events

Vision Service
- Register of success or failure when establishing the connection.
- Token management to identify different connections.
- List associated images.

Additionally, the required endpoints for coexistence with the 2060 Service Agent are provided. Keep in mind that this connection is mandatory for any chatbot that intends to utilize a 2060 agent.

- [UnicId API](#unicid-api)
  - [WebRTC Service](#webrtc-service)
    - [Call Event](#call-event)
        - [EventNotificationType](#eventnotificationtype)
  - [Vision Service](#vision-service)
    - [Link Media](#link-media)
    - [List Media](#list-media)
    - [Success](#success)
    - [Failure](#failure)

## WebRTC Service
The RESTful API includes the following options:

### Call event
Endpoint deployment for retrieving join and left notifications
```json
{
    "event": EventNotificationType
    "peerId": String
    "roomId": String
}
```
#### EventNotificationType
This section defines the type of event notifications related to the call events, such as joining or leaving a room.
- Peer Joined(`peer-joined`)
- Peer Left(`peer-left`)

## Vision Service
The RESTful API includes the following options:

### Link Media
Link multimedia content to a specific `token`: In this case, both the `token` generated during the process and the `mediaId` from the image in the data store must be sent.
```url
https://<baseUrl>/link/{token}/{mediaId}
```

### List Media
List multimedia content associated with the `token`: In this case, the `token` generated during the process must be sent.
```url
https://<baseUrl>/list/{token}
```

### Success
Success notification for the vision process: In this case, the `token` generated during the process must be sent along with the notification.
```url
https://<baseUrl>/success/{token}
```

### Failure
Failure notification for the vision process: In this case, the `token` generated during the process must be sent along with the notification.
```url
https://<baseUrl>/failure/{token}
```

**Note**: For more information, please refer to the Open API documentation available in the app