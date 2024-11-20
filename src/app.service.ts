import {
  BaseMessage,
  ConnectionStateUpdated,
  ContextualMenuItem,
  ContextualMenuSelectMessage,
  ContextualMenuUpdateMessage,
  CredentialReceptionMessage,
  EMrtdDataSubmitMessage,
  MediaMessage,
  MenuSelectMessage,
  MrzDataRequestMessage,
  MrzDataSubmitMessage,
  ProfileMessage,
  TextMessage,
} from '@2060.io/model'
import { ApiClient, ApiVersion } from '@2060.io/service-agent-client'
import { EventHandler } from '@2060.io/nestjs-client'
import { Injectable, Logger } from '@nestjs/common'
import { SessionEntity } from './models'
import { CredentialState, JsonTransformer } from '@credo-ts/core'
import { Cmd, StateStep } from './common'
import { Repository } from 'typeorm'
import { InjectRepository } from '@nestjs/typeorm'
import { I18nService } from 'nestjs-i18n'

@Injectable()
export class CoreService implements EventHandler {
  private readonly apiClient: ApiClient
  private readonly logger = new Logger(CoreService.name)

  constructor(
    @InjectRepository(SessionEntity)
    private readonly sessionRepository: Repository<SessionEntity>,
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
        inMsg = JsonTransformer.fromJSON(message, MenuSelectMessage)
        content = inMsg.menuItems[0].id ?? null
        break
      case MediaMessage.type:
        inMsg = JsonTransformer.fromJSON(message, MediaMessage)
        content = 'media'
        break
      case ProfileMessage.type:
        inMsg = JsonTransformer.fromJSON(message, ProfileMessage)
        await this.sessionRepository.update(session.id, {
          ...session,
          lang: inMsg.preferredLanguage,
        })
        await this.welcomeMessage(session.connectionId)
        await this.sendMrzRequest(session)
        break
      case MrzDataSubmitMessage.type:
        content = JsonTransformer.fromJSON(message, MrzDataSubmitMessage)
        await this.sendText(session.connectionId, 'MRZ_SUCCESSFULL', session.lang)
        break
      case EMrtdDataSubmitMessage.type:
        content = JsonTransformer.fromJSON(message, EMrtdDataSubmitMessage)
        break
      case CredentialReceptionMessage.type:
        inMsg = JsonTransformer.fromJSON(message, CredentialReceptionMessage)
        switch (inMsg.state) {
          case CredentialState.Done:
            break
          case CredentialState.Declined:
            break
          default:
            break
        }
        break
      default:
        break
    }

    if (content != null) {
      content = content.trim()
      if (content.length === 0) content = null
    }
    if (content == null) return

    await this.handleStateInput(content, session)
    await this.sendRootMenu(session)
  }

  async newConnection(event: ConnectionStateUpdated): Promise<void> {
    await this.handleSession(event.connectionId)
  }

  private async handleContextualAction(selectionId: string, session: SessionEntity) {
    switch (session.state) {
      case StateStep.START:
        if(selectionId===Cmd.CREATE){
          this.sessionRepository.update(session.id, {
            state: StateStep.MRZ
          })
          await this.sendMrzRequest(session)
        }
        break
      case StateStep.MRZ:
        if(selectionId===Cmd.ABORT){
          this.sessionRepository.update(session.id, {
            state: StateStep.START
          })
        }
        break
      default:
        break
    }
  }
  
  private async handleStateInput(content: any, session: SessionEntity) {
    switch (session.state) {
      case StateStep.START:
        await this.sendText(session.connectionId, "HELP", session.lang)
        break
      default:
        break
    }
  }
  
  private async welcomeMessage(connectionId: string) {
    const lang = (await this.handleSession(connectionId)).lang
    await this.sendText(connectionId, 'WELCOME', lang)
  }
  
  private async handleSession(connectionId: string): Promise<SessionEntity> {
    let session = await this.sessionRepository.findOneBy({
      connectionId: connectionId,
    })
    this.logger.log('inputMessage session: ' + session)
  
    if (!session) {
      session = this.sessionRepository.create({
        connectionId: connectionId,
        state: StateStep.START,
      })
  
      await this.sessionRepository.save(session)
      this.logger.log('New session: ' + session)
    }
    return session
  }
  
  private async sendMrzRequest(session: SessionEntity) {
    if(session.state==StateStep.START){
      await this.sendText(session.connectionId, 'MRZ_REQUEST', session.lang)
      await this.apiClient.messages.send(
        new MrzDataRequestMessage({
          connectionId: session.connectionId,
        })
      )      
    }
  }

  private async sendText(connectionId: string, text: string, lang: string) {
    await this.apiClient.messages.send(
      new TextMessage({
        connectionId: connectionId,
        content: this.getText(text, lang),
      })
    )
  }

  private getText(text: string, lang: string): string {
    return this.i18n.t(`msg.${text}`, { lang: lang })
  }

  private async sendRootMenu(session: SessionEntity){
    const item: ContextualMenuItem[] = []
    switch (session.state) {
      case StateStep.START:
        break
      case StateStep.MRZ:
        item.push(
          new ContextualMenuItem({
            id: Cmd.ABORT,
            title: this.getText("CMD.ABORT", session.lang)
          })
        )
        break
      default:
        break
    }

    await this.apiClient.messages.send(
      new ContextualMenuUpdateMessage({
        title: this.getText("ROOT_TITTLE", session.lang),
        connectionId: session.connectionId,
        options: item
      })      
    )
  }
}

