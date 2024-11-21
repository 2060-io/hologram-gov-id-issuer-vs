// notification-request.dto.ts
import { IsEnum, IsNotEmpty, IsString } from 'class-validator'
import { EventNotificationType } from '../common'

export class NotificationRequest {
  @IsEnum(EventNotificationType)
  event: EventNotificationType

  @IsString()
  @IsNotEmpty()
  peerId: string

  @IsString()
  @IsNotEmpty()
  roomId: string
}
