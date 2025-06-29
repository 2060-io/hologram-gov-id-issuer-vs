# Setting up a development environment with docker

![2060 logo](https://raw.githubusercontent.com/2060-io/.github/44bf28569fec0251a9367a9f6911adfa18a01a7c/profile/assets/2060_logo.svg)

In this document, you will learn how to setup a development environment for working/modifying the unic-id backend.

## Setup a SSL port forwarding service

For being able to test your service directly from your cellphone, you will need a port forwarding service.
In this example, we will use the ngrok service. The free version of ngrok is enough for what we need.

First, follow installation instructions on ngrok web site. Then edit your ngrok config file, add your authtoken and configure the tunnel section with the following:

```
authtoken: <your ngrok token>
tunnels:
    serviceagent:
        addr: 3001
        proto: http
        schemes:
            - https
    backend:
        addr: 2902
        proto: http
        schemes:
            - https
version: "2"
region: us
```

Then, start ngrok service:

```
$ ngrok start --config=ngrok-config.yml --all
ngrok                                                                                                              (Ctrl+C to quit)

Build better APIs with ngrok. Early access: ngrok.com/early-access

Session Status                online
Account                       (Plan: Free)
Version                       3.5.0
Region                        United States (us)
Latency                       92ms
Web Interface                 http://127.0.0.1:4040
Forwarding                    https://04e8-190-24-98-158.ngrok-free.app -> http://localhost:2903
Forwarding                    https://11ee-190-24-98-158.ngrok-free.app -> http://localhost:2904
Forwarding                    https://59cd-190-24-98-158.ngrok-free.app -> http://localhost:3001
```

## Prepare docker-compose and start the containers

Copy the docker-compose-template file to docker.compose.yml and set your nrogk endpoints

```
version: '3.3'

services:
# the service-agent is the container that will handle all the didcomm messaging.
# Your backend will use it to send and receive messages, receive new connection notifications, issue credentials, verify credentials,...
# Port 3000 provide an internal API for your backend and should only be visible by your backend. For details regarding the service agent API, go to http://localhost:3000 after having started the container.
# Port 3001 must be publicly available through a https public URL. DIDComm messaging App will connect to this public endpoint.
  service-agent:
    image: io2060/2060-service-agent:dev
    networks:
      - chatbot
    ports:
      - 3000:3000
      - 3001:3001
    environment:
# set service-agent did:web domain: here replace p3001.testing.2060.io with the ngrok domain name that will forward to port 3001
      - AGENT_PUBLIC_DID=did:web:p3001.testing.2060.io
# set service-agent websocket public endpoint: here replace p3001.testing.2060.io with the ngrok domain name that will forward to port 3001
      - AGENT_ENDPOINT=wss://p3001.testing.2060.io
# set service-agent agent websocket public endpoint: here replace p3001.testing.2060.io with the ngrok domain name that will forward to port 3001
      - ANONCREDS_SERVICE_BASE_URL=https://p3001.testing.2060.io
# set service-agent icon image: here replace p2903.testing.2060.io with the ngrok domain name that will forward to port 2903 (the backend project you will run locally)
# make sure an avatar.png image is present in src/main/resources/META-INF/resources of the backend project
      - AGENT_INVITATION_IMAGE_URL=https://p2903.testing.2060.io/avatar.png
# set service-agent service name
      - AGENT_NAME=Gov Id Issuer
      - USE_CORS=true
# where to send the receive events: here replace p2903.testing.2060.io with the ngrok domain name that will forward to port 2903 (the backend project you will run locally)
      - EVENTS_BASE_URL=https://p2903.testing.2060.io
  
  unic-id-dts:
    build: 
      context: ../
      dockerfile: Dockerfile
    image: 2060-unic-id-dts
    container_name: unic-id-dts
    restart: always
    networks:
      - chatbot
    ports:
      - 2802:5000
    environment:
      - AGENT_PORT=5000
      - SERVICE_AGENT_ADMIN_URL=http://service-agent:2800
      - POSTGRES_HOST=postgres
      - POSTGRES_USER=gaia
      - POSTGRES_PASSWORD=2060demo
      - VISION_URL=https://vision.dev.2060.io
      - WEBRTC_SERVER_URL=https://dts-webrtc.dev.2060.io
      - PUBLIC_BASE_URL=<your backend ngrok url>
      - ID_VERIFICATION_TIMEOUT_SECONDS=900
      - LOG_LEVEL=3
  
  postgres:
    image: postgres:15.2
    networks:
      - chatbot
    ports:
      - 5432:5432
    environment:
      - POSTGRES_PASSWORD=2060demo
      - POSTGRES_USER=gaia 
      - PGDATA=/var/lib/postgresql/data/pgdata

networks:
  chatbot:
    ipam:
      driver: default
      config:
        - subnet: 172.28.0.0/27
```

Start the containers:

```
$ docker-compose up -d
```
verify containers are started:

```
$ docker ps
CONTAINER ID   IMAGE                                                 COMMAND                  CREATED         STATUS         PORTS                                                                                                               NAMES
e5a115362594   2060-unic-id-dts                "docker-entrypoint.s…"   4 hours ago   Exited (137) 4 hours ago             unic-id-dts
100b146e717c   io2060/2060-service-agent:dev   "/bin/sh -c 'yarn st…"   3 seconds ago   Up 3 seconds   0.0.0.0:3000-3001->3000-3001/tcp                                                                                    docker-service-agent-1
12b7355f9b34   postgres:15.2                                         "docker-entrypoint.s…"   3 seconds ago   Up 3 seconds   0.0.0.0:5432->5432/tcp                                                                                              docker-postgres-1
```


### Scan the Agent QR Code with the Hologram App and start using your service

- With your browser, connect to the service-agent public endpoint (the ngrok URL that forwards to port 3001) and append /qr
- scan the QR code to create the connection

![QR Code](assets/browser.png)

- accept the invitation and use the service

<kbd>
<img src="assets/IMG_7715.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
<img src="assets/IMG_7716.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
</kbd>
