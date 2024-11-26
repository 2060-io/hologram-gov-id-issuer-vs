// src/dto/create-room-request.dto.ts
import { IsString, IsInt, IsNotEmpty } from 'class-validator'
import { Exclude, Expose } from 'class-transformer'

@Exclude()
export class CreateRoomRequest {
  @Expose()
  @IsString()
  @IsNotEmpty()
  eventNotificationUri: string

  @Expose()
  @IsInt()
  maxPeerCount: number

  constructor(eventNotificationUri: string, maxPeerCount: number) {
    this.eventNotificationUri = eventNotificationUri
    this.maxPeerCount = maxPeerCount
  }
}
