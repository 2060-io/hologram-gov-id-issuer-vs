import { Injectable, Logger } from '@nestjs/common'
import { PeerType } from '@/common'
import { PeerRegistry, SessionEntity } from '@/models'
import { InjectRepository } from '@nestjs/typeorm'
import { JoinCallRequest, NotificationRequest } from '@/dto'
import { Repository } from 'typeorm'
import { utils } from '@credo-ts/core'
import { instanceToPlain } from 'class-transformer'

@Injectable()
export class WebrtcService {
  private readonly logger = new Logger(WebrtcService.name)

  constructor(
    @InjectRepository(PeerRegistry)
    private readonly peerRepository: Repository<PeerRegistry>,
    @InjectRepository(SessionEntity)
    private readonly sessionRepository: Repository<SessionEntity>,
  ) {}

  async joinCall(notificationRequest: NotificationRequest): Promise<void> {
    const peer = await this.updatePeerRegistry(notificationRequest)

    if (peer.type === PeerType.PEER_USER) {
      const session = await this.sessionRepository.findOneBy({
        connectionId: peer.connectionId,
      })

      const crv = new PeerRegistry()
      const peerId = utils.uuid()
      crv.id = peerId
      crv.connectionId = null
      crv.roomId = peer.roomId
      crv.wsUrl = peer.wsUrl
      crv.type = PeerType.PEER_VISION

      await this.peerRepository.save(crv)

      const joinCallRequest = new JoinCallRequest()
      joinCallRequest.wsUrl = `${peer.wsUrl}/?roomId=${peer.roomId}&peerId=${peerId}`
      joinCallRequest.callbackBaseUrl = process.env.PUBLIC_BASE_URL
      joinCallRequest.datastoreBaseUrl = process.env.DATASTORE_URL
      joinCallRequest.token = session.id // TODO: validate token
      joinCallRequest.lang = session.lang

      this.logger.log(`joinCall: token: ${JSON.stringify(instanceToPlain(joinCallRequest))}`)

      await fetch(`${process.env.VISION_URL}/join-call`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(instanceToPlain(joinCallRequest)),
      })
    }
  }

  async leaveCall(notificationRequest: NotificationRequest): Promise<void> {
    await this.updatePeerRegistry(notificationRequest)
  }

  async updatePeerRegistry(notificationRequest: NotificationRequest): Promise<PeerRegistry> {
    const peer = await this.peerRepository.findOneBy({ id: notificationRequest.peerId })
    if (!peer) {
      throw new Error(`No call found for peerId: ${notificationRequest.peerId}`)
    }

    peer.event = notificationRequest.event
    await this.peerRepository.save(peer)
    return peer
  }
}
