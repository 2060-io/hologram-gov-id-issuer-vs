import {
  BaseMessage,
  CallOfferRequestMessage,
  Claim,
  ConnectionStateUpdated,
  ContextualMenuItem,
  ContextualMenuSelectMessage,
  ContextualMenuUpdateMessage,
  CredentialIssuanceMessage,
  CredentialReceptionMessage,
  CredentialTypeInfo,
  EMrtdDataRequestMessage,
  EMrtdDataSubmitMessage,
  MediaMessage,
  MenuDisplayMessage,
  MenuItem,
  MenuSelectMessage,
  MrzDataRequestMessage,
  MrzDataSubmitMessage,
  ProfileMessage,
  TextMessage,
} from '@2060.io/service-agent-model'
import { ApiClient, ApiVersion } from '@2060.io/service-agent-client'
import { EventHandler } from '@2060.io/service-agent-nestjs-client'
import { Injectable, Logger } from '@nestjs/common'
import { CredentialEntity, PeerEntity, SessionEntity } from '@/models'
import { CredentialState, JsonTransformer, Sha256, utils } from '@credo-ts/core'
import { Cmd, formatBirthDate, MenuSelectEnum, PeerType, StateStep } from '@/common'
import { Repository } from 'typeorm'
import { InjectRepository } from '@nestjs/typeorm'
import { I18nService } from 'nestjs-i18n'
import { CreateRoomRequest, WebRtcCallDataV1 } from '@/dto'
import { fetch, FormData } from 'undici'

@Injectable()
export class CoreService implements EventHandler {
  private readonly apiClient: ApiClient
  private readonly logger = new Logger(CoreService.name)

  constructor(
    @InjectRepository(SessionEntity)
    private readonly sessionRepository: Repository<SessionEntity>,
    @InjectRepository(PeerEntity)
    private readonly peerRepository: Repository<PeerEntity>,
    @InjectRepository(CredentialEntity)
    private readonly credentialRepository: Repository<CredentialEntity>,
    private readonly i18n: I18nService,
  ) {
    const baseUrl = process.env.SERVICE_AGENT_ADMIN_BASE_URL || ''
    const apiVersion = (process.env.API_VERSION as ApiVersion) || ApiVersion.V1
    this.apiClient = new ApiClient(baseUrl, apiVersion)
  }

  async inputMessage(message: BaseMessage): Promise<void> {
    let content = null
    let inMsg = null
    let session: SessionEntity = null

    this.logger.debug('inputMessage: ' + JSON.stringify(message))

    session = await this.handleSession(message.connectionId)

    switch (message.type) {
      case TextMessage.type:
        content = JsonTransformer.fromJSON(message, TextMessage)
        break
      case ContextualMenuSelectMessage.type:
        inMsg = JsonTransformer.fromJSON(message, ContextualMenuSelectMessage)
        await this.handleContextualAction(inMsg.selectionId, session)
        break
      case MenuSelectMessage.type:
        inMsg = message as MenuSelectMessage
        session = await this.handleMenuselection(inMsg.menuItems?.[0]?.id, session)
        break
      case MediaMessage.type:
        inMsg = JsonTransformer.fromJSON(message, MediaMessage)
        content = 'media'
        break
      case ProfileMessage.type:
        inMsg = JsonTransformer.fromJSON(message, ProfileMessage)
        session.lang = inMsg.preferredLanguage
        await this.welcomeMessage(session.connectionId)
        session = await this.sendMrzRequest(session)
        break
      case MrzDataSubmitMessage.type:
        content = JsonTransformer.fromJSON(message, MrzDataSubmitMessage)
        break
      case EMrtdDataSubmitMessage.type:
        content = JsonTransformer.fromJSON(message, EMrtdDataSubmitMessage)
        break
      case CredentialReceptionMessage.type:
        content = JsonTransformer.fromJSON(message, CredentialReceptionMessage)
        break
      default:
        break
    }

    if (content != null) {
      if (typeof content === 'string') content = content.trim()
      if (content.length === 0) content = null
    }

    await this.handleStateInput(content, session)
  }

  async newConnection(event: ConnectionStateUpdated): Promise<void> {
    const session = await this.handleSession(event.connectionId)
    await this.sendContextualMenu(session)
    await this.sendCredentialType()
  }

  async closeConnection(event: ConnectionStateUpdated): Promise<void> {
    const session = await this.handleSession(event.connectionId)
    await this.purgeUserData(session)
  }

  private async welcomeMessage(connectionId: string) {
    const lang = (await this.handleSession(connectionId)).lang
    await this.sendText(connectionId, 'WELCOME', lang)
  }

  private async startVideoCall(session: SessionEntity): Promise<SessionEntity> {
    const createRoom = new CreateRoomRequest(`${process.env.PUBLIC_BASE_URL}/call-event`, 50)
    const response = await fetch(`${process.env.WEBRTC_URL}/rooms/${utils.uuid()}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(createRoom),
    })
    const wsUrl: WebRtcCallDataV1 = JsonTransformer.fromJSON(await response.json(), WebRtcCallDataV1)

    this.logger.log(`Create new room ${wsUrl.roomId} on: ${wsUrl.wsUrl}`)

    const peer = this.peerRepository.create({
      id: utils.uuid(),
      connectionId: session.connectionId,
      roomId: wsUrl.roomId,
      wsUrl: wsUrl.wsUrl,
      type: PeerType.PEER_USER,
    })

    await this.peerRepository.save(peer)
    this.logger.log('New peer: ' + JSON.stringify(peer))

    await this.sendText(session.connectionId, 'FACE_REQUEST', session.lang)

    await this.apiClient.messages.send(
      new CallOfferRequestMessage({
        connectionId: session.connectionId,
        parameters: {
          ...wsUrl,
          peerId: peer.id,
        },
      }),
    )

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
        if (selectionId === Cmd.ABORT) {
          session = await this.abortVerification(session)
        }
        break
      default:
        break
    }
    return await this.sessionRepository.save(session)
  }

  async handleStateInput(content: any, session: SessionEntity): Promise<SessionEntity> {
    session = await this.timeoutSession(session)
    switch (session.state) {
      case StateStep.START:
        if (content !== null) await this.sendText(session.connectionId, 'HELP', session.lang)
        break
      case StateStep.MRZ:
        if (content instanceof MrzDataSubmitMessage) {
          await this.sendText(session.connectionId, 'MRZ_SUCCESSFULL', session.lang)
          session = await this.sendEMrtdRequest(session, content.threadId)
          // TODO: is a MRZ valid?
        }
        break
      case StateStep.EMRTD:
        if (content instanceof EMrtdDataSubmitMessage) {
          await this.sendText(session.connectionId, 'EMRTD_SUCCESSFULL', session.lang)
          session.state = StateStep.VERIFICATION
          session.credentialMetadata = {
            documentType: content.dataGroups.processed.documentType ?? null,
            documentNumber: content.dataGroups.processed.documentNumber ?? null,
            issuingState: content.dataGroups.processed.issuingState ?? null,
            firstName: content.dataGroups.processed.firstName ?? null,
            lastName: content.dataGroups.processed.lastName ?? null,
            sex: content.dataGroups.processed.sex ?? null,
            nationality: content.dataGroups.processed.nationality ?? null,
            birthDate: formatBirthDate(content.dataGroups.processed.dateOfBirth) ?? null,
            issuanceDate: content.dataGroups.processed.issuingState ?? null, // TODO: review
            expirationDate: content.dataGroups.processed.dateOfExpiry ?? null,
            facePhoto: content.dataGroups.processed.faceImages[0] ?? null,
          }
          session = await this.sendDataStore(session)
          session = await this.startVideoCall(session)
        }
        break
      case StateStep.VERIFICATION:
        if (content === 'success') {
          session.state = StateStep.ISSUE
          await this.sendCredentialData(session)
        } else if (content === 'failure') await this.sendMenuSelection(session)
        break
      case StateStep.ISSUE:
        if (content instanceof CredentialReceptionMessage) {
          // Generate credential and delete if it exists
          await this.handleCredential(session)
          switch (content.state) {
            case CredentialState.Done:
              await this.sendText(session.connectionId, 'CREDENTIAL_ACCEPTED', session.lang)
              session = await this.purgeUserData(session)
              await this.sendText(session.connectionId, 'NEW_CREDENTIAL', session.lang)
              break
            case CredentialState.Declined:
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
    return await this.sendContextualMenu(session)
  }

  async handleMenuselection(id: string, session: SessionEntity): Promise<SessionEntity> {
    switch (session.state) {
      case StateStep.VERIFICATION:
        if (id === MenuSelectEnum.CONFIRM_YES_VALUE) session = await this.startVideoCall(session)
        if (id === MenuSelectEnum.CONFIRM_NO_VALUE) {
          session.state = StateStep.START
          session = await this.abortVerification(session)
        }
        break
      default:
        break
    }
    return await this.sessionRepository.save(session)
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

  private async handleCredential(session: SessionEntity) {
    // encrypt credential
    const hashString =
      session.credentialMetadata.birthDate +
      session.credentialMetadata.nationality +
      session.credentialMetadata.documentNumber // TODO: review hash is better with mrz
    const encrypt = new Sha256().hash(hashString)

    let credential = await this.credentialRepository.findOneBy({
      hash: Buffer.from(encrypt),
    })
    this.logger.debug('handleCredential credential: ' + JSON.stringify(credential))

    if (credential) {
      await this.credentialRepository.remove(credential)
      this.logger.debug('Existing credential removed: ' + JSON.stringify(credential))
    }

    if (!credential) {
      credential = this.credentialRepository.create({
        connectionId: session.connectionId,
        hash: Buffer.from(encrypt),
        revocationId: utils.uuid(),
      })

      await this.credentialRepository.save(credential)
      this.logger.debug('New credential: ' + JSON.stringify(credential))
    }
  }

  private async sendMenuSelection(session: SessionEntity): Promise<SessionEntity> {
    let prompt = ''
    const menuitems: MenuItem[] = []
    switch (session.state) {
      case StateStep.VERIFICATION:
        prompt = this.getText('MENU_SELECT.PROMPT.VERIFICATION', session.lang)
        menuitems.push({
          id: MenuSelectEnum.CONFIRM_YES_VALUE,
          text: this.getText('MENU_SELECT.OPTIONS.YES', session.lang),
        })
        menuitems.push({
          id: MenuSelectEnum.CONFIRM_NO_VALUE,
          text: this.getText('MENU_SELECT.OPTIONS.NO', session.lang),
        })
        break
      default:
        return session
    }
    const updatedMenuItems = menuitems.map(item => ({
      ...item,
      action: item.action || '',
    }))
    await this.apiClient.messages.send(
      new MenuDisplayMessage({
        connectionId: session.connectionId,
        prompt: prompt,
        menuItems: updatedMenuItems,
      }),
    )
    return await this.sessionRepository.save(session)
  }

  private async sendText(connectionId: string, text: string, lang: string) {
    await this.apiClient.messages.send(
      new TextMessage({
        connectionId: connectionId,
        content: this.getText(text, lang),
      }),
    )
  }

  private getText(text: string, lang: string): string {
    return this.i18n.t(`msg.${text}`, { lang: lang })
  }

  private async sendMrzRequest(session: SessionEntity): Promise<SessionEntity> {
    if (session.state == StateStep.START) {
      session.state = StateStep.MRZ
      await this.sendText(session.connectionId, 'MRZ_REQUEST', session.lang)
      await this.apiClient.messages.send(
        new MrzDataRequestMessage({
          connectionId: session.connectionId,
        }),
      )
    }
    return await this.sessionRepository.save(session)
  }

  private async sendEMrtdRequest(session: SessionEntity, threadId: string): Promise<SessionEntity> {
    if (session.state == StateStep.MRZ) {
      session.state = StateStep.EMRTD
      await this.sendText(session.connectionId, 'EMRTD_REQUEST', session.lang)
      await this.apiClient.messages.send(
        new EMrtdDataRequestMessage({
          connectionId: session.connectionId,
          threadId: threadId,
        }),
      )
    }
    return await this.sessionRepository.save(session)
  }

  private async sendContextualMenu(session: SessionEntity): Promise<SessionEntity> {
    const item: ContextualMenuItem[] = []
    switch (session.state) {
      case StateStep.START:
        item.push(
          new ContextualMenuItem({
            id: Cmd.CREATE,
            title: this.getText('CMD.CREATE', session.lang),
          }),
        )
        break
      case StateStep.MRZ:
      case StateStep.EMRTD:
      case StateStep.VERIFICATION:
        item.push(
          new ContextualMenuItem({
            id: Cmd.ABORT,
            title: this.getText('CMD.ABORT', session.lang),
          }),
        )
        break
      default:
        break
    }

    await this.apiClient.messages.send(
      new ContextualMenuUpdateMessage({
        title: this.getText('ROOT_TITTLE', session.lang),
        connectionId: session.connectionId,
        options: item,
        timestamp: new Date(),
      }),
    )
    return await this.sessionRepository.save(session)
  }

  private async sendCredentialData(session: SessionEntity): Promise<SessionEntity> {
    const claims: Claim[] = []

    if (session.credentialMetadata) {
      Object.entries(session.credentialMetadata).forEach(([key, value]) => {
        claims.push(
          new Claim({
            name: key,
            value: value ?? null,
          }),
        )
      })
      claims.push(
        new Claim({
          name: 'issued',
          value: new Date().toDateString(),
        }),
      )
    }

    await this.sendText(session.connectionId, 'CREDENTIAL_OFFER', session.lang)
    const credentialId = (await this.apiClient.credentialTypes.getAll())[0].id
    await this.apiClient.messages.send(
      new CredentialIssuanceMessage({
        connectionId: session.connectionId,
        credentialDefinitionId: credentialId,
        claims: claims,
      }),
    )

    this.logger.debug('sendCredential with claims: ' + JSON.stringify(claims))
    return session
  }

  private async sendCredentialType(): Promise<void> {
    const credential: CredentialTypeInfo[] = await this.apiClient.credentialTypes.getAll()

    if (!credential || credential.length === 0) {
      await this.apiClient.credentialTypes.create({
        id: utils.uuid(),
        name: process.env.CREDENTIAL_NAME,
        version: '1.0',
        attributes: [
          'documentType',
          'documentNumber',
          'issuingState',
          'firstName',
          'lastName',
          'sex',
          'nationality',
          'birthDate',
          'issuanceDate',
          'expirationDate',
          'facePhoto',
          'issued',
        ],
      })
    }
  }

  private async sendDataStore(session: SessionEntity): Promise<SessionEntity> {
    try {
      // Create registry on data store
      const createResponse = await fetch(`${process.env.DATASTORE_URL}/c/${session.id}/1?token=null`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
      })
      if (!createResponse.ok) {
        throw new Error(
          `Create registry failed with status ${createResponse.status}: ${createResponse.statusText}`,
        )
      }

      // Upload registry on data store
      const base64Data = session.credentialMetadata.facePhoto.split(',')[1]
      const binaryData = Buffer.from(base64Data, 'base64')
      const formData = new FormData()
      const blob = new Blob([binaryData], { type: 'application/octet-stream' })
      formData.append('chunk', blob)

      const uploadResponse = await fetch(`${process.env.DATASTORE_URL}/u/${session.id}/0?token=null`, {
        method: 'PUT',
        headers: {},
        body: formData,
      })
      if (!uploadResponse.ok) {
        const errorText = await uploadResponse.text()
        throw new Error(
          `Upload registry failed with status ${uploadResponse.status}: ${uploadResponse.statusText}. Response body: ${errorText}`,
        )
      }

      this.logger.debug('sendDataStore: Data uploaded')
    } catch (error) {
      this.logger.error(`sendDataStore: Canon't save data - ${error}`)
    }
    return session
  }

  // Special flows
  private async purgeUserData(session): Promise<SessionEntity> {
    session.state = StateStep.START
    session.userAgent = null
    session.tp = null
    session.nfcSupport = null
    session.credentialMetadata = null
    return await this.sessionRepository.save(session)
  }

  private async abortVerification(session: SessionEntity): Promise<SessionEntity> {
    session.state = StateStep.START
    await this.sendText(session.connectionId, 'ABORT_PROCESS', session.lang)
    return await this.purgeUserData(session)
  }

  private async timeoutSession(session: SessionEntity): Promise<SessionEntity> {
    const timeoutEnv = Number(process.env.ID_VERIFICATION_TIMEOUT_SECONDS) || 900
    if (!session.updatedTs) {
      throw new Error('The session entity does not have a valid updatedTs value')
    }
    const now = new Date()
    const updatedTime = new Date(session.updatedTs)
    const timeDifferenceInSeconds = Math.floor((now.getTime() - updatedTime.getTime()) / 1000)

    if (timeoutEnv && timeDifferenceInSeconds > timeoutEnv) {
      session.state = StateStep.TIMEOUT
      await this.sendText(session.connectionId, 'TIMEOUT_PROCESS', session.lang)
      this.logger.debug(`Session with ID ${session.id} has expired`)
      return await this.purgeUserData(session)
    }
    return session
  }
}
