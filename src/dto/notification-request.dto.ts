// notification-request.dto.ts
import { IsEnum, IsNotEmpty, IsString } from 'class-validator'
import { EventNotificationType } from '@/common'
import { Expose } from 'class-transformer'
import { ApiProperty } from '@nestjs/swagger'

export class NotificationRequest {
  @IsEnum(EventNotificationType)
  @ApiProperty({
    description: 'The type of event notification.',
    enum: EventNotificationType,
  })
  event: EventNotificationType

  @ApiProperty({
    description: 'The ID of the peer in the WebRTC session.',
    example: 'peer-12345',
  })
  @IsString()
  @IsNotEmpty()
  @Expose()
  peerId: string

  @ApiProperty({
    description: 'The ID of the room where the event occurred.',
    example: 'room-67890',
  })
  @IsString()
  @IsNotEmpty()
  @Expose()
  roomId: string
}
