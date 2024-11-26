// notification-request.dto.ts
import { IsEnum, IsNotEmpty, IsString } from 'class-validator'
import { EventNotificationType } from '@/common'
import { Expose } from 'class-transformer'

export class NotificationRequest {
  @IsEnum(EventNotificationType)
  event: EventNotificationType

  @IsString()
  @IsNotEmpty()
  @Expose()
  peerId: string

  @IsString()
  @IsNotEmpty()
  @Expose()
  roomId: string
}
