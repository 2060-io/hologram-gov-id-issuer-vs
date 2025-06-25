# Hologram Gov Id Issuer API and deployment guide

- [Hologram Gov Id Issuer API and deployment guide](#hologram-gov-id-issuer-api-and-deployment-guide)
  - [Initial Considerations](#initial-considerations)
  - [API](#api)
    - [WebRTC Service](#webrtc-service)
      - [Call event](#call-event)
        - [EventNotificationType](#eventnotificationtype)
    - [Vision Service](#vision-service)
      - [List Media](#list-media)
      - [Success](#success)
      - [Failure](#failure)

## Initial Considerations
To deploy the service locally, ensure that the environment variables in the application file are configured as follows:

| **Variable**                   | **Description**                                | **default**                          |
|--------------------------------|------------------------------------------------|--------------------------------------|
| `AGENT_PORT`                   | The port on which the agent runs               | `5000`                               |
| `SERVICE_AGENT_ADMIN_URL` | Base URL for the service agent admin API       | `http://localhost.com`      |
| `POSTGRES_HOST`                | Hostname for the PostgreSQL database           | `postgres`                           |
| `POSTGRES_USER`                | Username for the PostgreSQL database           | `gaia`                               |
| `POSTGRES_PASSWORD`            | Password for the PostgreSQL database           | `password`                           |
| `VISION_URL`                   | URL for the Vision Service                     | `http://localhost.com/`         |
| `WEBRTC_SERVER_URL`                   | URL for the WebRTC Service                     | `http://localhost.com/`     |
| `PUBLIC_BASE_URL`              | Public-facing base URL for the service         | `http://localhost.com/`      |
| `ID_VERIFICATION_TIMEOUT_SECONDS` | Timeout for ID verification in seconds       | `900`                                |
| `LOG_LEVEL`                    | Logging verbosity level (1-5)                  | `3`                                  |

Once everything is set up correctly, you just need to navigate to the `docker-dev` folder and run the command to start Docker Compose. This will bring up all the necessary components that coexist with the project. 

## API

This document outlines the user interface provided by Gov ID Issuer for its integration with various services that either depend on it or are required for its proper functionality.

Gov ID Issuer App API consists on a REST-like interface that exposes endpoints to:

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