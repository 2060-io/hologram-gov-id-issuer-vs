import { Expose } from 'class-transformer'
import { IsString } from 'class-validator'

export abstract class WebRtcCallData {
  @Expose()
  @IsString()
  protocol: string

  @Expose()
  @IsString()
  roomId: string

  @Expose()
  @IsString()
  wsUrl: string
}

export class WebRtcCallDataV1 extends WebRtcCallData {
  constructor() {
    super()
    this.protocol = '2060-mediasoup-v1'
  }
}
