# UnicId
## Description
This application is a Decentralized Trust Service aimed to generate Verifiable Credentials from the reading of official travel documents and biometric verification. For more details see [README](/README.md)

- [UnicId](#unicid)
  - [Description](#description)
  - [Initial Considerations](#initial-considerations)
  - [UnicId API](#unicid-api)
    - [WebRTC Service](#webrtc-service)
      - [Call Event](#call-event)
          - [EventNotificationType](#eventnotificationtype)
    - [Vision Service](#vision-service)
      - [Link Media](#link-media)
      - [List Media](#list-media)
      - [Success](#success)
      - [Failure](#failure)

## Initial Considerations
To deploy the service locally, ensure that the variables in the application.properties file are configured as follows:
```properties
%dev.io.unicid.vision.redirdomain=https://<publicBaseUrl>
%dev.io.unicid.registry.res.c.WebRtcResource/mp-rest/url=https://<publicWebRtcServiceUrl>/
%dev.io.unicid.registry.res.c.VisionResource/mp-rest/url=https://<publicVisionServiceUrl>
```

Once everything is set up correctly, you just need to navigate to the `docker-dev` folder and run the command to start Docker Compose. This will bring up all the necessary components that coexist with the project. Finally, you can test the project by running `mvn clean quarkus:dev` in the root folder.

## UnicId API

This document outlines the user interface provided by UnicId for its integration with various services that either depend on it or are required for its proper functionality.

UnicId API consists on a REST-like interface that exposes endpoints to:

WebRTC Service
- Handle service events by logging connections and creating rooms for new connections
- Listen for the service's response to potential events

Vision Service
- Register of success or failure when establishing the connection.
- Token management to identify different connections.
- List associated images.

Additionally, the required endpoints for coexistence with the [2060 Service Agent](https://github.com/2060-io/2060-service-agent) are provided. Keep in mind that this connection is mandatory for any chatbot that intends to utilize a DIDComm agent.

### WebRTC Service
The RESTful API includes the following options:
For more information, check out the [WebRTC Service](https://github.com/2060-io/webrtc-server).

#### Call event
Endpoint deployment for retrieving join and left notifications
For more information, check out the [create rooms](https://github.com/2060-io/webrtc-server?tab=readme-ov-file#parameters-all-optional) section in the WebRTC server documentation.

```json
{
    "event": EventNotificationType
    "peerId": String
    "roomId": String
}
```
##### EventNotificationType
This section defines the type of event notifications related to the call events, such as joining or leaving a room.
- Peer Joined(`peer-joined`)
- Peer Left(`peer-left`)

### Vision Service
The RESTful API includes the following options:

#### List Media
List multimedia content associated with the `token`: In this case, the `token` generated during the process must be sent.
```url
https://<baseUrl>/list/{token}
```

#### Success
Success notification for the vision process: In this case, the `token` generated during the process must be sent along with the notification.
```url
https://<baseUrl>/success/{token}
```

#### Failure
Failure notification for the vision process: In this case, the `token` generated during the process must be sent along with the notification.
```url
https://<baseUrl>/failure/{token}
```

**Note**: For more information, please refer to the Open API documentation available in the app