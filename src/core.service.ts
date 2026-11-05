import {
  ActionMenuOption,
  ApiClient,
  ConnectionEntity,
  CredentialDefinition,
  CredentialService,
  CredentialState,
  EmrtdDataReceivedData,
  EventEnvelope,
  EventHandler,
  isEventEnvelope,
  MrtdProblemReportReason,
  UnknownEventEnvelope,
  ValidResponse,
  VS_AGENT_CLIENT,
} from '@verana-labs/vs-agent-nestjs-client'
import { Inject, Injectable, Logger, OnModuleInit } from '@nestjs/common'
import { WebRtcPeerEntity, SessionEntity } from '@/models'
import { randomUUID } from 'crypto'
import { plainToInstance } from 'class-transformer'
import { validateOrReject } from 'class-validator'
import { Cmd, MenuSelectEnum, PeerType, processEmrtdData, StateStep } from '@/common'
import { Repository } from 'typeorm'
import { InjectRepository } from '@nestjs/typeorm'
import { I18nService } from 'nestjs-i18n'
import { CreateRoomRequest, WebRtcCallDataV1 } from '@/dto'
import { ConfigService } from '@nestjs/config'
import { whereAlpha3 } from 'iso-3166-1'

/**
 * Reason shown to the user when an MRZ or eMRTD read does not complete.
 * The values are the MRTD submit states of the v1 Admin API, so the message text is unchanged.
 */
export enum MrtdFailureReason {
  Declined = 'declined',
  Timeout = 'timeout',
  Error = 'error',
}

/**
 * Input of the state machine: a text-like input (user text, 'media', or the vision service
 * outcomes 'success' | 'failure' | 'error'), or one of the VS Agent events below.
 */
export type StateInput =
  | string
  | null
  | { kind: 'mrz'; valid: boolean }
  | { kind: 'emrtd'; dataGroups: EmrtdDataReceivedData['dataGroups'] }
  | { kind: 'mrtd-problem'; document: 'mrz' | 'emrtd'; reason: MrtdFailureReason }
  | { kind: 'credential'; state: CredentialState }

type StateEvent = Exclude<StateInput, string | null>

function isInput<K extends StateEvent['kind']>(
  content: StateInput,
  kind: K,
): content is Extract<StateEvent, { kind: K }> {
  return typeof content === 'object' && content !== null && content.kind === kind
}

const MRTD_PROBLEM_REASONS: Record<
  MrtdProblemReportReason,
  { document: 'mrz' | 'emrtd'; reason: MrtdFailureReason }
> = {
  'e.p.mrz-refused': { document: 'mrz', reason: MrtdFailureReason.Declined },
  'e.p.mrz-timeout': { document: 'mrz', reason: MrtdFailureReason.Timeout },
  'e.p.emrtd-refused': { document: 'emrtd', reason: MrtdFailureReason.Declined },
  'e.p.emrtd-timeout': { document: 'emrtd', reason: MrtdFailureReason.Timeout },
}

@Injectable()
export class CoreService implements EventHandler, OnModuleInit {
  private readonly logger = new Logger(CoreService.name)

  // Nest builds several instances of this service (event handler, core module, app module):
  // they share one lookup/creation of the credential definition.
  private static credentialDefinition?: Promise<CredentialDefinition>

  constructor(
    @InjectRepository(SessionEntity)
    private readonly sessionRepository: Repository<SessionEntity>,
    @InjectRepository(WebRtcPeerEntity)
    private readonly peerRepository: Repository<WebRtcPeerEntity>,
    @InjectRepository(ConnectionEntity)
    private readonly connRepository: Repository<ConnectionEntity>,
    private readonly credentialService: CredentialService,
    private readonly i18n: I18nService,
    private readonly configService: ConfigService,
    @Inject(VS_AGENT_CLIENT) private readonly apiClient: ApiClient,
  ) {}

  async onModuleInit(): Promise<void> {
    // Best effort at startup: the VS Agent may not be reachable yet, issuance retries it.
    try {
      const definition = await this.ensureCredentialDefinition()
      this.logger.log(`Credential definition ready: ${definition.id}`)
    } catch (error) {
      this.logger.warn(`Credential definition not ready yet: ${error}`)
    }
  }

  async onEvent(envelope: EventEnvelope | UnknownEventEnvelope): Promise<void> {
    if (!isEventEnvelope(envelope)) return
    const event = envelope

    let connectionId: string | undefined
    switch (event.type) {
      case 'didcomm.basic-messages.message-received':
      case 'didcomm.action-menu.perform-received':
      case 'didcomm.question-answer.answer-received':
      case 'didcomm.media-sharing.share-media-received':
      case 'didcomm.mrtd.mrz-data-received':
      case 'didcomm.mrtd.emrtd-data-received':
      case 'didcomm.mrtd.problem-report-received':
      case 'didcomm.calls.call-reject-received':
        connectionId = event.data.connectionId
        break
      case 'didcomm.credential-exchanges.state-updated':
        if (event.data.role !== 'issuer' || !['done', 'declined', 'abandoned'].includes(event.data.state))
          return
        connectionId = event.data.connectionId
        break
      default:
        return
    }
    if (!connectionId) return

    let content: StateInput = null
    let session: SessionEntity = null

    try {
      this.logger.debug(`onEvent: ${event.type} ${event.id}`)

      session = await this.handleSession(connectionId)

      switch (event.type) {
        case 'didcomm.basic-messages.message-received':
          content = event.data.content
          break
        case 'didcomm.action-menu.perform-received':
          await this.handleContextualAction(event.data.name, session)
          break
        case 'didcomm.question-answer.answer-received':
          session = await this.handleMenuselection(
            this.menuSelectionId(event.data.response, session),
            session,
          )
          break
        case 'didcomm.media-sharing.share-media-received':
          content = 'media'
          break
        case 'didcomm.mrtd.mrz-data-received': {
          session.threadId = event.data.threadId
          const valid = event.data.mrzData?.parsed?.valid === true
          content = { kind: 'mrz', valid }
          if (!valid) {
            await this.sendText(session.connectionId, 'MRZ_INVALID', session.lang)
            await this.sendMenuSelection(session)
          }
          break
        }
        case 'didcomm.mrtd.emrtd-data-received':
          content = { kind: 'emrtd', dataGroups: event.data.dataGroups }
          break
        case 'didcomm.mrtd.problem-report-received': {
          const problem = MRTD_PROBLEM_REASONS[event.data.reason]
          if (problem) content = { kind: 'mrtd-problem', ...problem }
          break
        }
        case 'didcomm.credential-exchanges.state-updated':
          content = { kind: 'credential', state: event.data.state }
          break
        case 'didcomm.calls.call-reject-received':
          await this.sendText(session.connectionId, 'CALL_REJECT', session.lang)
          await this.sendMenuSelection(session)
          break
        default:
          break
      }

      if (typeof content === 'string') {
        content = content.trim()
        if (content.length === 0) content = null
      }
    } catch (error) {
      this.logger.error(`onEvent: ${error}`)
    }
    if (session) await this.handleStateInput(content, session)
  }

  async newConnection(connectionId: string): Promise<void> {
    let session = await this.handleSession(connectionId)
    const conn = await this.connRepository.findOneBy({ id: session.connectionId })
    session.lang = conn?.userProfile?.preferredLanguage ?? session.lang

    if (session.state == StateStep.START) {
      await this.sendText(session.connectionId, 'WELCOME', session.lang)
      session = await this.sendMrzRequest(session)
    } else if (session.state === StateStep.INCOMPATIBLE_DEVICE)
      await this.sendText(session.connectionId, 'NFC_UNSUPPORTED', session.lang)
    await this.sendContextualMenu(session)
  }

  async closeConnection(connectionId: string): Promise<void> {
    const session = await this.handleSession(connectionId)
    await this.purgeUserData(session)
  }

  private async startVideoCall(session: SessionEntity): Promise<SessionEntity> {
    const createRoom = new CreateRoomRequest(
      `${this.configService.get<string>('appConfig.publicBaseUrl')}/call-event`,
      50,
    )
    try {
      const response = await fetch(`${this.configService.get<string>('appConfig.webRtcServerUrl')}/rooms`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(createRoom),
      })
      if (!response.ok) {
        const errorText = await response.text()
        throw new Error(`Request failed with status ${response.status}: ${errorText}`)
      }

      const wsUrl = plainToInstance(WebRtcCallDataV1, await response.json())
      await validateOrReject(wsUrl)

      this.logger.log(`Create new room ${wsUrl.roomId} on: ${wsUrl.wsUrl}`)

      const peer = this.peerRepository.create({
        id: randomUUID(),
        connectionId: session.connectionId,
        roomId: wsUrl.roomId,
        wsUrl: wsUrl.wsUrl,
        type: PeerType.PEER_USER,
      })

      await this.peerRepository.save(peer)
      this.logger.log('New peer: ' + JSON.stringify(peer))

      await this.apiClient.didcomm.offerCall({
        connectionId: session.connectionId,
        callType: 'service',
        offerExpirationTime: new Date(
          Date.now() + this.configService.get<number>('appConfig.verificationTimeout') * 1000,
        ).toISOString(),
        description: this.getText('FACE_REQUEST', session.lang),
        parameters: {
          ...wsUrl,
          peerId: peer.id,
        },
      })
    } catch (error) {
      this.logger.error(`startVideoCall: ${error}`)
      await this.handleStateInput('error', session)
    }

    return session
  }

  private async handleContextualAction(selectionId: string, session: SessionEntity): Promise<SessionEntity> {
    switch (session.state) {
      case StateStep.START:
        if (selectionId === Cmd.CREATE) {
          session = await this.sendMrzRequest(session)
        }
        break
      case StateStep.MRZ:
      case StateStep.EMRTD:
      case StateStep.VERIFICATION:
      case StateStep.ISSUE:
        if (selectionId === Cmd.ABORT) {
          session = await this.abortVerification(session)
        }
        break
      default:
        break
    }
    return await this.sessionRepository.save(session)
  }

  async handleStateInput(content: StateInput, session: SessionEntity): Promise<SessionEntity> {
    try {
      session = await this.timeoutSession(session)
      switch (session.state) {
        case StateStep.START:
          if (content !== null) await this.sendText(session.connectionId, 'HELP', session.lang)
          break
        case StateStep.MRZ:
          if (isInput(content, 'mrz')) {
            await this.sendText(session.connectionId, 'MRZ_SUCCESSFUL', session.lang)
            // TODO: When MRZ can checked add to the message "Congratulations, your document is compatible."
            session = await this.sendEMrtdRequest(session)
            // TODO: is a MRZ valid?
          } else if (isInput(content, 'mrtd-problem') && content.document === 'mrz') {
            await this.handleMrtdDataSubmitError(session, content.reason)
          }
          break
        case StateStep.EMRTD:
          if (isInput(content, 'emrtd')) {
            // validate authenticity and integrity
            const { dataGroups } = content
            const verification = dataGroups?.verification

            if (verification) {
              const isVerified = verification?.authenticity === true && verification?.integrity === true

              if (!isVerified) {
                this.logger.warn(
                  `[eMRTD] Verification failed. authenticity=${String(verification?.authenticity)} integrity=${String(verification?.integrity)}`,
                )
                await this.sendText(session.connectionId, 'EMRTD_VERIFICATION_FAILED', session.lang)
                await this.sendMenuSelection(session)
                break
              }
            } else {
              this.logger.log('[eMRTD] No verification provided; proceeding without checks')
            }

            const processed = processEmrtdData(dataGroups)
            if (!processed) {
              this.logger.warn('[eMRTD] The received data groups could not be decoded')
              await this.handleMrtdDataSubmitError(session, MrtdFailureReason.Error)
              break
            }

            session.mrzData = processed.mrzString

            await this.sendText(session.connectionId, 'EMRTD_SUCCESSFUL', session.lang)
            session.state = StateStep.VERIFICATION
            const credentialIssuanceDate = `${new Date().getFullYear()}${String(new Date().getMonth() + 1).padStart(2, '0')}${String(new Date().getDate()).padStart(2, '0')}`
            const rawSex = processed.sex ?? 'X'
            session.credentialClaims = {
              documentType: processed.documentType ?? null,
              documentNumber: processed.documentNumber ?? null,
              documentIssuingState: whereAlpha3(processed.issuingState)?.alpha2 ?? null,
              firstName: processed.firstName ?? null,
              lastName: processed.lastName ?? null,
              sex: ['M', 'F'].includes(rawSex) ? rawSex : 'X',
              nationality: whereAlpha3(processed.nationality)?.alpha2 ?? null,
              birthDate: processed.dateOfBirth ?? null,
              credentialIssuanceDate,
              documentExpirationDate: processed.dateOfExpiry ?? null,
              facePhoto: processed.faceImages[0] ?? null,
              mrzData: processed.mrzString,
            }
            session = await this.startVideoCall(session)
          } else if (isInput(content, 'mrtd-problem') && content.document === 'emrtd') {
            await this.handleMrtdDataSubmitError(session, content.reason)
          }
          break
        case StateStep.VERIFICATION:
          if (content === 'success') {
            await this.sendText(session.connectionId, 'VERIFICATION_SUCCESSFUL', session.lang)
            session.state = StateStep.ISSUE
            await this.sendCredentialData(session)
          } else if (content === 'failure') await this.sendMenuSelection(session)
          else if (content === 'error') {
            session = await this.purgeUserData(session)
            await this.sendText(session.connectionId, 'CALL_VISION_ERROR', session.lang)
          }
          break
        case StateStep.ISSUE:
          if (isInput(content, 'credential')) {
            switch (content.state) {
              case 'done':
                await this.sendText(session.connectionId, 'CREDENTIAL_ACCEPTED', session.lang)
                session = await this.purgeUserData(session)
                await this.sendText(session.connectionId, 'NEW_CREDENTIAL', session.lang)
                break
              case 'declined':
              case 'abandoned':
                await this.sendText(session.connectionId, 'CREDENTIAL_REJECTED', session.lang)
                session = await this.abortVerification(session)
                break
              default:
                break
            }
          }
          break
        default:
          break
      }
    } catch (error) {
      this.logger.error('handleStateInput: ' + error)
    }
    return await this.sendContextualMenu(session)
  }

  private async handleMrtdDataSubmitError(session: SessionEntity, reason: MrtdFailureReason) {
    const text = this.getText('MRTD_FAILED', session.lang).replace('<reason>', reason)
    await this.apiClient.didcomm.sendBasicMessage({ connectionId: session.connectionId, content: text })
    await this.sendMenuSelection(session)
  }

  async handleMenuselection(id: string, session: SessionEntity): Promise<SessionEntity> {
    const handleYesAction = async (session: SessionEntity): Promise<SessionEntity> => {
      switch (session.state) {
        case StateStep.MRZ:
          return this.sendMrzRequest(session)
        case StateStep.EMRTD:
          return this.sendEMrtdRequest(session)
        case StateStep.VERIFICATION:
          return this.startVideoCall(session)
        default:
          return session
      }
    }

    const handleNoAction = async (session: SessionEntity): Promise<SessionEntity> => {
      return this.abortVerification(session)
    }

    if (id === MenuSelectEnum.CONFIRM_YES_VALUE) {
      session = await handleYesAction(session)
    } else if (id === MenuSelectEnum.CONFIRM_NO_VALUE) {
      session = await handleNoAction(session)
    }

    return this.sessionRepository.save(session)
  }

  /**
   * A v2 question answer carries the text of the selected response, not an id:
   * map it back to the menu item that offered that text.
   */
  private menuSelectionId(response: string, session: SessionEntity): string {
    const answer = response?.trim()
    if (answer === this.getText('MENU_SELECT.OPTIONS.YES', session.lang))
      return MenuSelectEnum.CONFIRM_YES_VALUE
    if (answer === this.getText('MENU_SELECT.OPTIONS.NO', session.lang))
      return MenuSelectEnum.CONFIRM_NO_VALUE
    return answer
  }

  private async handleSession(connectionId: string): Promise<SessionEntity> {
    let session = await this.sessionRepository.findOneBy({
      connectionId: connectionId,
    })
    this.logger.debug('handleSession session: ' + JSON.stringify(session))

    if (!session) {
      session = this.sessionRepository.create({
        connectionId: connectionId,
        state: StateStep.START,
      })

      await this.sessionRepository.save(session)
      this.logger.debug('New session: ' + JSON.stringify(session))
    }
    return await this.sessionRepository.save(session)
  }

  private async sendMenuSelection(session: SessionEntity): Promise<SessionEntity> {
    let prompt = ''
    const validResponses: ValidResponse[] = []
    switch (session.state) {
      case StateStep.MRZ:
      case StateStep.EMRTD:
      case StateStep.VERIFICATION:
        prompt = this.getText('MENU_SELECT.PROMPT.VERIFICATION', session.lang)
        validResponses.push({ text: this.getText('MENU_SELECT.OPTIONS.YES', session.lang) })
        validResponses.push({ text: this.getText('MENU_SELECT.OPTIONS.NO', session.lang) })
        break
      default:
        return session
    }
    await this.apiClient.didcomm.sendQuestion({
      connectionId: session.connectionId,
      question: prompt,
      validResponses,
    })
    return await this.sessionRepository.save(session)
  }

  private async sendText(connectionId: string, text: string, lang: string) {
    await this.apiClient.didcomm.sendBasicMessage({ connectionId, content: this.getText(text, lang) })
  }

  private getText(text: string, lang: string): string {
    return this.i18n.t(`msg.${text}`, { lang: lang })
  }

  private async sendMrzRequest(session: SessionEntity): Promise<SessionEntity> {
    session.state = StateStep.MRZ
    await this.sendText(session.connectionId, 'MRZ_REQUEST', session.lang)
    await this.apiClient.didcomm.requestMrz({ connectionId: session.connectionId })
    return await this.sessionRepository.save(session)
  }

  private async sendEMrtdRequest(session: SessionEntity): Promise<SessionEntity> {
    session.state = StateStep.EMRTD
    await this.sendText(session.connectionId, 'EMRTD_REQUEST', session.lang)
    // The v2 request carries no thread id: the agent starts a new MRTD thread on the connection.
    await this.apiClient.didcomm.requestEmrtdData({ connectionId: session.connectionId })
    return await this.sessionRepository.save(session)
  }

  private async sendContextualMenu(session: SessionEntity): Promise<SessionEntity> {
    const options: ActionMenuOption[] = []
    switch (session.state) {
      case StateStep.START:
        options.push({ name: Cmd.CREATE, title: this.getText('CMD.CREATE', session.lang), description: '' })
        break
      case StateStep.MRZ:
      case StateStep.EMRTD:
      case StateStep.VERIFICATION:
      case StateStep.ISSUE:
        options.push({ name: Cmd.ABORT, title: this.getText('CMD.ABORT', session.lang), description: '' })
        break
      default:
        break
    }

    await this.apiClient.didcomm.sendMenu({
      connectionId: session.connectionId,
      menu: { title: this.getText('ROOT_TITTLE', session.lang), description: '', options },
    })
    return await this.sessionRepository.save(session)
  }

  /**
   * Looks up the AnonCreds credential definition of the configured VTJSC and creates it when
   * none exists, so that restarts reuse the same definition.
   */
  private ensureCredentialDefinition(): Promise<CredentialDefinition> {
    const jsonSchemaCredentialId = this.configService.get<string>('appConfig.jsonSchemaCredentialId')
    if (!jsonSchemaCredentialId) {
      return Promise.reject(new Error('JSON_SCHEMA_CREDENTIAL_ID is not set'))
    }
    if (!CoreService.credentialDefinition) {
      CoreService.credentialDefinition = this.credentialService
        .createCredentialDefinition(jsonSchemaCredentialId, { supportRevocation: true })
        .catch(error => {
          CoreService.credentialDefinition = undefined
          throw error
        })
    }
    return CoreService.credentialDefinition
  }

  // Generate credential and revoke the previous one issued for the same document, if any
  private async sendCredentialData(session: SessionEntity): Promise<SessionEntity> {
    const { id: credentialDefinitionId } = await this.ensureCredentialDefinition()
    await this.credentialService.issue(session.credentialClaims, {
      connectionId: session.connectionId,
      refId: session.mrzData,
      revokeIfAlreadyIssued: true,
      credentialDefinitionId,
    })
    await this.sendText(session.connectionId, 'CREDENTIAL_OFFER', session.lang)

    this.logger.debug('sendCredential with claims: ' + JSON.stringify(session.credentialClaims))
    return session
  }

  // Special flows
  private async purgeUserData(session): Promise<SessionEntity> {
    session.state = StateStep.START
    session.threadId = null
    session.userAgent = null
    session.tp = null
    session.credentialClaims = null
    session.mrzData = null
    return await this.sessionRepository.save(session)
  }

  private async abortVerification(session: SessionEntity): Promise<SessionEntity> {
    session.state = StateStep.START
    await this.sendText(session.connectionId, 'ABORT_PROCESS', session.lang)
    return await this.purgeUserData(session)
  }

  private async timeoutSession(session: SessionEntity): Promise<SessionEntity> {
    const timeoutEnv = this.configService.get<number>('appConfig.verificationTimeout')
    if (!session.updatedTs) {
      throw new Error('The session entity does not have a valid updatedTs value')
    }
    const now = new Date()
    const updatedTime = new Date(session.updatedTs)
    const timeDifferenceInSeconds = Math.floor((now.getTime() - updatedTime.getTime()) / 1000)

    if (timeoutEnv && timeDifferenceInSeconds > timeoutEnv && session.state !== StateStep.ISSUE) {
      session.state = StateStep.TIMEOUT
      await this.sendText(session.connectionId, 'TIMEOUT_PROCESS', session.lang)
      this.logger.debug(`Session with ID ${session.id} has expired`)
      return await this.purgeUserData(session)
    }
    return session
  }
}
