import { Injectable, Logger } from '@nestjs/common'
import { PeerType } from '@/common'
import { WebRtcPeerEntity, SessionEntity } from '@/models'
import { InjectRepository } from '@nestjs/typeorm'
import { JoinCallRequest, NotificationRequest } from '@/dto'
import { Repository } from 'typeorm'
import { utils } from '@credo-ts/core'
import { instanceToPlain } from 'class-transformer'
import { ConfigService } from '@nestjs/config'

@Injectable()
export class WebrtcService {
  private readonly logger = new Logger(WebrtcService.name)

  constructor(
    @InjectRepository(WebRtcPeerEntity)
    private readonly peerRepository: Repository<WebRtcPeerEntity>,
    @InjectRepository(SessionEntity)
    private readonly sessionRepository: Repository<SessionEntity>,
    private readonly configService: ConfigService,
  ) {}

  async joinCall(notificationRequest: NotificationRequest): Promise<void> {
    const peer = await this.updateWebRtcPeerEntity(notificationRequest)

    if (peer.type === PeerType.PEER_USER) {
      const session = await this.sessionRepository.findOneBy({
        connectionId: peer.connectionId,
      })

      const crv = new WebRtcPeerEntity()
      const peerId = utils.uuid()
      crv.id = peerId
      crv.connectionId = null
      crv.roomId = peer.roomId
      crv.wsUrl = peer.wsUrl
      crv.type = PeerType.PEER_VISION

      await this.peerRepository.save(crv)

      const joinCallRequest = new JoinCallRequest()
      joinCallRequest.wsUrl = `${peer.wsUrl}/?roomId=${peer.roomId}&peerId=${peerId}`
      joinCallRequest.callbackBaseUrl = this.configService.get<string>('appConfig.publicBaseUrl')
      joinCallRequest.token = session.id
      joinCallRequest.lang = session.lang

      this.logger.log(`joinCall: token: ${JSON.stringify(instanceToPlain(joinCallRequest))}`)

      await fetch(`${this.configService.get<string>('appConfig.visionUrl')}/join-call`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(instanceToPlain(joinCallRequest)),
      })
    }
  }

  async leaveCall(notificationRequest: NotificationRequest): Promise<void> {
    await this.updateWebRtcPeerEntity(notificationRequest)
  }

  async updateWebRtcPeerEntity(notificationRequest: NotificationRequest): Promise<WebRtcPeerEntity> {
    const peer = await this.peerRepository.findOneBy({ id: notificationRequest.peerId })
    if (!peer) {
      throw new Error(`No call found for peerId: ${notificationRequest.peerId}`)
    }

    peer.event = notificationRequest.event
    await this.peerRepository.save(peer)
    return peer
  }
}
