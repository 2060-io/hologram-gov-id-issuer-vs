// src/dto/join-call-request.dto.ts
import { IsOptional, IsString } from 'class-validator'
import { Exclude, Expose } from 'class-transformer'

@Exclude()
export class JoinCallRequest {
  @IsOptional()
  @IsString()
  @Expose({ name: 'ws_url' })
  wsUrl?: string

  @IsOptional()
  @IsString()
  @Expose({ name: 'datastore_base_url' })
  datastoreBaseUrl?: string

  @IsOptional()
  @IsString()
  @Expose({ name: 'callback_base_url' })
  callbackBaseUrl?: string

  @IsOptional()
  @IsString()
  token?: string

  @IsOptional()
  @IsString()
  lang?: string
}
