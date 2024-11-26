import { Global, Module } from '@nestjs/common'
import { CredentialEntity, WebRtcPeerEntity, SessionEntity } from '@/models'
import { CoreService } from '@/core.service'
import { TypeOrmModule, TypeOrmModuleOptions } from '@nestjs/typeorm'
import { ConnectionEntity } from '@2060.io/service-agent-nestjs-client'

const defaultOptions = {
  type: 'postgres',
  host: process.env.POSTGRES_HOST,
  port: 5432,
  username: process.env.POSTGRES_USER,
  password: process.env.POSTGRES_PASSWORD,
  database: process.env.POSTGRES_USER,
  schema: 'public',
  synchronize: true,
  ssl: false,
  logging: false,
} as TypeOrmModuleOptions

@Global()
@Module({
  imports: [
    TypeOrmModule.forFeature([ConnectionEntity, CredentialEntity, WebRtcPeerEntity, SessionEntity]),
    TypeOrmModule.forRoot({
      ...defaultOptions,
      entities: [ConnectionEntity, CredentialEntity, WebRtcPeerEntity, SessionEntity],
    }),
  ],
  controllers: [],
  providers: [CoreService],
  exports: [TypeOrmModule, CoreService],
})
export class CoreModule {}
