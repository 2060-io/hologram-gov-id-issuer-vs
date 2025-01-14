import {
  BaseMessage,
  CallOfferRequestMessage,
  CallRejectRequestMessage,
  ConnectionStateUpdated,
  ContextualMenuItem,
  ContextualMenuSelectMessage,
  ContextualMenuUpdateMessage,
  CredentialReceptionMessage,
  EMrtdDataRequestMessage,
  EMrtdDataSubmitMessage,
  MediaMessage,
  MenuDisplayMessage,
  MenuItem,
  MenuSelectMessage,
  MrtdSubmitState,
  MrzDataRequestMessage,
  MrzDataSubmitMessage,
  ProfileMessage,
  TextMessage,
} from '@2060.io/service-agent-model'
import { ApiClient, ApiVersion } from '@2060.io/service-agent-client'
import { CredentialService, EventHandler } from '@2060.io/service-agent-nestjs-client'
import { Injectable, Logger, OnModuleInit } from '@nestjs/common'
import { WebRtcPeerEntity, SessionEntity } from '@/models'
import { CredentialState, JsonTransformer, utils } from '@credo-ts/core'
import { Cmd, MenuSelectEnum, PeerType, StateStep } from '@/common'
import { Repository } from 'typeorm'
import { InjectRepository } from '@nestjs/typeorm'
import { I18nService } from 'nestjs-i18n'
import { CreateRoomRequest, WebRtcCallDataV1 } from '@/dto'
import { fetch } from 'undici'
import { ConfigService } from '@nestjs/config'
import { whereAlpha3 } from 'iso-3166-1'

@Injectable()
export class CoreService implements EventHandler, OnModuleInit {
  private readonly apiClient: ApiClient
  private readonly logger = new Logger(CoreService.name)

  constructor(
    @InjectRepository(SessionEntity)
    private readonly sessionRepository: Repository<SessionEntity>,
    @InjectRepository(WebRtcPeerEntity)
    private readonly peerRepository: Repository<WebRtcPeerEntity>,
    private readonly credentialService: CredentialService,
    private readonly i18n: I18nService,
    private readonly configService: ConfigService,
  ) {
    const baseUrl = configService.get<string>('appConfig.serviceAgentAdminUrl')
    this.apiClient = new ApiClient(baseUrl, ApiVersion.V1)
  }

  async onModuleInit() {
    await this.credentialService.create(
      [
        'documentType',
        'documentNumber',
        'documentIssuingState',
        'firstName',
        'lastName',
        'sex',
        'nationality',
        'birthDate',
        'credentialIssuanceDate',
        'documentExpirationDate',
        'facePhoto',
      ],
      {
        name: 'Unic Id',
        supportRevocation: true,
        maximumCredentialNumber: 1000,
        autoRevocationEnabled: true,
      },
    )
  }

  async inputMessage(message: BaseMessage): Promise<void> {
    let content = null
    let inMsg = null
    let session: SessionEntity = null

    try {
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
          if (session.state == StateStep.START) session = await this.sendMrzRequest(session)
          break
        case MrzDataSubmitMessage.type:
          session.threadId = message.threadId
          content = JsonTransformer.fromJSON(message, MrzDataSubmitMessage)
          break
        case EMrtdDataSubmitMessage.type:
          content = JsonTransformer.fromJSON(message, EMrtdDataSubmitMessage)
          break
        case CredentialReceptionMessage.type:
          content = JsonTransformer.fromJSON(message, CredentialReceptionMessage)
          break
        case CallRejectRequestMessage.type:
          await this.sendText(session.connectionId, 'CALL_REJECT', session.lang)
          await this.sendMenuSelection(session)
          break
        default:
          break
      }

      if (content != null) {
        if (typeof content === 'string') content = content.trim()
        if (content.length === 0) content = null
      }
    } catch (error) {
      this.logger.error(`inputMessage: ${error}`)
    }
    await this.handleStateInput(content, session)
  }

  async newConnection(event: ConnectionStateUpdated): Promise<void> {
    const session = await this.handleSession(event.connectionId)
    await this.sendContextualMenu(session)
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
    const createRoom = new CreateRoomRequest(
      `${this.configService.get<string>('appConfig.publicBaseUrl')}/call-event`,
      50,
    )
    const response = await fetch(`${this.configService.get<string>('appConfig.webRtcServerUrl')}/rooms`, {
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

    await this.apiClient.messages.send(
      new CallOfferRequestMessage({
        connectionId: session.connectionId,
        offerExpirationTime: new Date(
          Date.now() + this.configService.get<number>('appConfig.verificationTimeout') * 1000,
        ),
        description: this.getText('FACE_REQUEST', session.lang),
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
    try {
      session = await this.timeoutSession(session)
      switch (session.state) {
        case StateStep.START:
          if (content !== null) await this.sendText(session.connectionId, 'HELP', session.lang)
          break
        case StateStep.MRZ:
          if (content instanceof MrzDataSubmitMessage) {
            if (content.state === MrtdSubmitState.Submitted) {
              await this.sendText(session.connectionId, 'MRZ_SUCCESSFUL', session.lang) // TODO: When MRZ can checked add to the message "Congratulations, your document is compatible with UnicID."
              session = await this.sendEMrtdRequest(session)
              // TODO: is a MRZ valid?
            } else {
              await this.handleMrtdDataSubmitError(session, content.state)
            }
          }
          break
        case StateStep.EMRTD:
          if (content instanceof EMrtdDataSubmitMessage) {
            if (content.state === MrtdSubmitState.Submitted) {
              session.mrzData = content.dataGroups.raw.DG1
              await this.sendText(session.connectionId, 'EMRTD_SUCCESSFUL', session.lang)
              session.state = StateStep.VERIFICATION
              const credentialIssuanceDate = `${new Date().getFullYear()}${String(new Date().getMonth() + 1).padStart(2, '0')}${String(new Date().getDate()).padStart(2, '0')}`
              const rawSex = content.dataGroups.processed.sex ?? 'X'
              session.credentialClaims = {
                documentType: content.dataGroups.processed.documentType ?? null,
                documentNumber: content.dataGroups.processed.documentNumber ?? null,
                documentIssuingState: whereAlpha3(content.dataGroups.processed.issuingState).alpha2 ?? null,
                firstName: content.dataGroups.processed.firstName ?? null,
                lastName: content.dataGroups.processed.lastName ?? null,
                sex: ['M', 'F'].includes(rawSex) ? rawSex : 'X',
                nationality: whereAlpha3(content.dataGroups.processed.nationality).alpha2 ?? null,
                birthDate: content.dataGroups.processed.dateOfBirth ?? null,
                credentialIssuanceDate,
                documentExpirationDate: content.dataGroups.processed.dateOfExpiry ?? null,
                facePhoto: content.dataGroups.processed.faceImages[0] ?? null,
              }
              session = await this.startVideoCall(session)
            } else {
              await this.handleMrtdDataSubmitError(session, content.state)
            }
          }
          break
        case StateStep.VERIFICATION:
          if (content === 'success') {
            await this.sendText(session.connectionId, 'VERIFICATION_SUCCESSFUL', session.lang)
            session.state = StateStep.ISSUE
            await this.sendCredentialData(session)
          } else if (content === 'failure') await this.sendMenuSelection(session)
          break
        case StateStep.ISSUE:
          if (content instanceof CredentialReceptionMessage) {
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
    } catch (error) {
      this.logger.error('handleStateInput: ' + error)
    }
    return await this.sendContextualMenu(session)
  }

  private async handleMrtdDataSubmitError(session: SessionEntity, state: MrtdSubmitState) {
    const text = this.getText('MRTD_FAILED', session.lang).replace('<reason>', state)
    await this.apiClient.messages.send(
      new TextMessage({
        connectionId: session.connectionId,
        content: text,
      }),
    )
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
    const menuitems: MenuItem[] = []
    switch (session.state) {
      case StateStep.MRZ:
      case StateStep.EMRTD:
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
    session.state = StateStep.MRZ
    await this.sendText(session.connectionId, 'MRZ_REQUEST', session.lang)
    await this.apiClient.messages.send(
      new MrzDataRequestMessage({
        connectionId: session.connectionId,
      }),
    )
    return await this.sessionRepository.save(session)
  }

  private async sendEMrtdRequest(session: SessionEntity): Promise<SessionEntity> {
    session.state = StateStep.EMRTD
    await this.sendText(session.connectionId, 'EMRTD_REQUEST', session.lang)
    await this.apiClient.messages.send(
      new EMrtdDataRequestMessage({
        connectionId: session.connectionId,
        threadId: session.threadId,
      }),
    )
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

  // Generate credential and delete if it exists
  private async sendCredentialData(session: SessionEntity): Promise<SessionEntity> {
    await this.credentialService.issuance(session.connectionId, session.credentialClaims, {
      identifier: session.mrzData,
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
    session.nfcSupport = null
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
