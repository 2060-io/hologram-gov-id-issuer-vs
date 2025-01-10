// stats.service.ts
import { StatEnum, StatEvent } from '@/utils/stats'
import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common'
import { Container, Connection, Sender, create_container } from 'rhea'

@Injectable()
export class StatProducerService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(StatProducerService.name)
  private connection: Connection | null = null
  private sender: Sender | null = null
  private container: Container

  private readonly config = {
    host: 'localhost',
    port: 61616,
    queue: 'stats-queue',
    username: 'quarkus',
    password: 'Quar64270enbPi26',
    reconnectLimit: 10,
    threads: 1,
    delay: 1000,
  }

  constructor() {
    this.container = create_container()
    this.logger.log('StatProducerService instantiated')
  }

  async onModuleInit() {
    this.logger.log(`Initializing StatProducer with queue: ${this.config.queue}`)
    await this.connect()
  }

  async onModuleDestroy() {
    await this.disconnect()
  }

  private async connect() {
    try {
      this.connection = this.container.connect({
        host: this.config.host,
        port: this.config.port,
        username: this.config.username,
        password: this.config.password,
        reconnect: true,
        reconnect_limit: this.config.reconnectLimit,
      })

      this.sender = this.connection.open_sender(this.config.queue)

      this.sender.on('accepted', () => {
        this.logger.debug('Message accepted')
      })

      this.sender.on('rejected', context => {
        this.logger.error(`Message rejected: ${context.error}`)
      })

      this.logger.log('Successfully connected to ActiveMQ')
    } catch (error) {
      this.logger.error(`Failed to connect: ${error.message}`)
      throw error
    }
  }

  private async disconnect() {
    try {
      if (this.sender) {
        await this.sender.close()
      }
      if (this.connection) {
        await this.connection.close()
      }
      this.logger.log('Successfully disconnected from ActiveMQ')
    } catch (error) {
      this.logger.error(`Error disconnecting: ${error.message}`)
    }
  }

  async spool(
    statClass: string,
    entityId: string,
    statEnums: StatEnum[],
    ts: Date = new Date(),
    increment: number = 1,
  ): Promise<void> {
    const event = new StatEvent(entityId, statEnums, increment, ts, statClass)

    try {
      const msg = {
        body: JSON.stringify(event),
      }
      this.sender.send(msg)
    } catch (error) {
      this.logger.error(`Failed to send message: ${error.message}`)
      throw error
    }
  }

  async spoolSingle(
    statClass: string,
    entityId: string,
    statEnum: StatEnum,
    ts: Date,
    increment: number,
  ): Promise<void> {
    await this.spool(statClass, entityId, [statEnum], ts, increment)
  }
}
