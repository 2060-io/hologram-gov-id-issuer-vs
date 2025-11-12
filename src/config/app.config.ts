import { registerAs } from '@nestjs/config'

/**
 * Configuration for the application, including ports, database URIs, and service URLs.
 *
 * @returns {object} - An object containing the configuration settings for the application.
 */
export default registerAs('appConfig', () => ({
  /**
   * The port number on which the application will run.
   * Defaults to 5000 if APP_PORT is not set in the environment variables.
   * @type {number}
   */
  appPort: parseInt(process.env.AGENT_PORT) || 5000,

  /**
   * Hostname or IP address for the PostgreSQL database.
   * Defaults 'postgres' string if POSTGRES_HOST is not set in the environment variables.
   * @type {string}
   */
  postgresHost: process.env.POSTGRES_HOST || 'postgres',

  /**
   * Username for the PostgreSQL database.
   * Defaults 'govidissuer' string if POSTGRES_USER is not set in the environment variables.
   * @type {string}
   */
  postgresUser: process.env.POSTGRES_USER || 'govidissuer',

  /**
   * Password for the PostgreSQL database.
   * Defaults 'demo' string if POSTGRES_PASSWORD is not set in the environment variables.
   * @type {string}
   */
  postgresPassword: process.env.POSTGRES_PASSWORD || 'demo',

  /**
   * Base URL for the Service Agent Admin.
   * Defaults to 'http://localhost:3000' if SERVICE_AGENT_ADMIN_URL is not set in the environment variables.
   * @type {string}
   */
  serviceAgentAdminUrl: process.env.SERVICE_AGENT_ADMIN_URL || 'http://localhost:3000',

  /**
   * Base URL for the application.
   * Defaults to 'http://localhost:2902' if PUBLIC_BASE_URL is not set.
   * @type {string}
   */
  publicBaseUrl: process.env.PUBLIC_BASE_URL || 'http://localhost:2902',

  /**
   * WebRTC server URL for handling real-time communication.
   * Defaults to a development URL if WEBRTC_SERVER_URL is not set.
   * @type {string}
   */
  webRtcServerUrl: process.env.WEBRTC_SERVER_URL || 'https://webrtc.demos.2060.io',

  /**
   * Vision API URL for image processing or related features.
   * Defaults to a development URL if VISION_URL is not set.
   * @type {string}
   */
  visionUrl: process.env.VISION_URL || 'https://vision.demos.2060.io',

  /**
   * Timeout for ID verification, in seconds.
   * Defaults to 900 seconds (15 minutes) if ID_VERIFICATION_TIMEOUT_SECONDS is not set.
   * @type {number}
   */
  verificationTimeout: parseInt(process.env.ID_VERIFICATION_TIMEOUT_SECONDS) || 900,

  /**
   * Credential Schema ID that defines the structure and attributes of the credential.
   * This JSON Schema Credential was created directly in the Gov-ID service API
   * at https://dm.gov-id-org.demos.dev.2060.io.
   * Defaults to a testnet schema ID if CREDENTIAL_SCHEMA_ID is not set.
   * @type {string}
   */
  credentialSchemaId:
    process.env.CREDENTIAL_SCHEMA_ID ||
    'https://dm.gov-id-org.demos.dev.2060.io/vt/schemas-government-id-jsc.json',
}))
