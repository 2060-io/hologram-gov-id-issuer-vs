# Hologram Government ID Issuer Chart

This Helm chart deploys the necessary services for the Hologram Government ID Issuer system, including optional components like the `vs-agent-chart`, `vision-matcher-chart`, `vision-service-chart`, and `webrtc-server-chart`.

---

## Requirements

Before deploying this chart, ensure the following requirements are met:

- Kubernetes 1.20+.
- Helm 3.0+.
- Persistent storage configured for any enabled services requiring it.
- Proper DNS and TLS certificates if using custom domains.

---

## Getting Started

To deploy the chart, use the following command:

```bash
helm upgrade --install hologram-gov-id-issuer-vs-chart ./charts --namespace <namespace> --create-namespace
```

Replace `<namespace>` with your desired Kubernetes namespace.

Example with Custom Values
If you want to customize the deployment, create a `custom-values.yaml` file with your desired configuration and apply it as follows:

```
helm upgrade --install hologram-gov-id-issuer-vs-chart ./charts --namespace <namespace> -f custom-values.yaml
```

## Recommendations
- Always enable the vs-agent-chart: This is the only service recommended to be used alongside the main deployment. If you decide to uninstall the vs-agent-chart, it will remove all linked resources, potentially breaking the deployment.
- Avoid enabling unnecessary services unless required for your use case to minimize resource usage and complexity.

## Configuration
The `values.yaml` file contains the default configuration for the chart. Below are some key configurable values:

### 🛠 General Settings

| Key            | Description                             | Default Value |
| -------------- | --------------------------------------- | ------------- |
| `service.port` | Port for the main service               | `2903`        |
| `domain`       | Base domain for the deployment          | `example.io`  |
| `replicas`     | Number of replicas for the main service | `1`           |

---

### 🔧 Backend Settings

| Key                | Description                            | Default Value               |
| ------------------ | -------------------------------------- | --------------------------- |
| `backend.webrtc`   | URL for the WebRTC service             | `https://webrtc.example.io` |
| `backend.vision`   | URL for the Vision service             | `https://vision.example.io` |
| `backend.timeout`  | Timeout for backend requests (seconds) | `900`                       |
| `backend.logLevel` | Log level for the backend              | `3`                         |
| `backend.vsAgentAdminUrl` | URL of the VS Agent v2 Admin API | `http://issuer:3000`        |
| `backend.jsonSchemaCredentialId` | VTJSC URL of the passport credential schema (required) | `""` |
| `backend.eventsWebhookApiKey.existingSecret` | Secret holding the optional events webhook key | `""` |

---

### 🤖 vs-agent-chart Settings

The dependency is the VS Agent v2 chart (`vs-agent-chart` `v2.0.0-dev.105`, Admin API `/v2`). Its full list of values is in the [vs-agent chart](https://github.com/verana-labs/vs-agent/tree/main/charts/vs-agent).

| Key                                       | Description                                                        | Default Value                    |
| ----------------------------------------- | ------------------------------------------------------------------ | -------------------------------- |
| `vs-agent-chart.enabled`                  | Enable the vs-agent service                                        | `false`                          |
| `vs-agent-chart.image.repository` / `tag` | VS Agent image with the MRTD plugin                                | `veranalabs/vs-agent-mrtd` / `v2.0.0-dev.105` |
| `vs-agent-chart.eventsWebhookUrl`         | Where the agent posts events: the backend `POST /events`           | `http://issuer-vs:2903/events`   |
| `vs-agent-chart.adminApiTrustedNetworks`  | CIDR blocks allowed to call the Admin API; must include the backend pod | `""`                        |
| `vs-agent-chart.veranaCorporationId`, `veranaRpcEndpointUrl`, `veranaIndexerBaseUrl`, `veranaChainId`, `trustedEcsEcosystemDids` | Verana network settings required by the agent | `""` |
| `vs-agent-chart.database.enabled`         | Enable the PostgreSQL sidecar (also used by the backend)           | `false`                          |
| `vs-agent-chart.extraEnv`                 | Must provide `VERANA_ACCOUNT_MNEMONIC` from a Secret               | see `values.yaml`                |

## Enabling Dependencies
If you want to enable additional services, update the `values.yaml` file and set the corresponding `enabled` field to `true`. For more information about these services, refer to their respective repositories:

- Vision Service: https://github.com/2060-io/vision-service
- WebRTC Server: https://github.com/2060-io/webrtc-server
- Vision Matcher: https://github.com/2060-io/vision-matcher


## Uninstalling
To uninstall the chart, run:
```
helm uninstall hologram-gov-id-issuer-vs-chart --namespace <namespace>
```

If the `vs-agent-chart` was enabled, ensure you clean up any linked resources to avoid orphaned configurations.

## Notes
- Ensure the vs-agent-chart is properly configured if enabled, as it is tightly coupled with the main deployment.
- For troubleshooting, check the logs of the pods using kubectl logs.
- Always test custom configurations in a staging environment before deploying to production.
