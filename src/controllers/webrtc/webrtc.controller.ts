import { Body, Controller, HttpException, HttpStatus, Inject, Logger, Post } from '@nestjs/common'
import { WebrtcService } from './webrtc.service'
import { EventNotificationType } from '../../common'
import { NotificationRequest } from '../../dto'

@Controller()
export class WebrtcController {
  private readonly logger = new Logger(WebrtcController.name)

  @Inject(WebrtcService)
  private readonly service: WebrtcService

  @Post('/call-event')
  async callEvent(@Body() notificationRequest: NotificationRequest): Promise<string> {
    const { peerId, event, roomId } = notificationRequest

    if (!peerId || !event || !roomId) {
      throw new HttpException('event or peerId is missing', HttpStatus.BAD_REQUEST)
    }

    try {
      switch (event) {
        case EventNotificationType.PEER_JOINED:
          this.logger.log(`callEvent: PEER_JOINED: ${JSON.stringify(notificationRequest)}`)
          await this.service.joinCall(notificationRequest)
          break
        case EventNotificationType.PEER_LEFT:
          this.logger.log(`callEvent: PEER_LEFT: ${JSON.stringify(notificationRequest)}`)
          await this.service.leaveCall(notificationRequest)
          break
        default:
          this.logger.warn(`callEvent: Unknown event type: ${event}`)
          break
      }

      return 'State successfully updated'
    } catch (error) {
      this.logger.error('Error updating state', error)
      throw new HttpException('An error occurred while updating the state', HttpStatus.INTERNAL_SERVER_ERROR)
    }
  }
}
