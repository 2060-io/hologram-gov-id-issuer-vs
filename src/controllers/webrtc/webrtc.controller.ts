import { Body, Controller, HttpException, HttpStatus, Logger, Post } from '@nestjs/common'
import { WebrtcService } from './webrtc.service'
import { EventNotificationType } from '@/common'
import { NotificationRequest } from '@/dto'
import { ApiBody, ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger'

@ApiTags('WebRTC')
@Controller()
export class WebrtcController {
  private readonly logger = new Logger(WebrtcController.name)

  constructor(private readonly service: WebrtcService) {}

  @Post('/call-event')
  @ApiOperation({ summary: 'Handle WebRTC call events' })
  @ApiResponse({ status: 200, description: 'State successfully updated.' })
  @ApiResponse({ status: 400, description: 'Event, peerId, or roomId is missing.' })
  @ApiResponse({ status: 500, description: 'An error occurred while updating the state.' })
  @ApiBody({
    type: NotificationRequest,
    description: 'The notification request payload, including event type, peerId, and roomId.',
  })
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
