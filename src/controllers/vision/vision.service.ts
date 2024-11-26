import { Injectable, Logger } from '@nestjs/common'
import { SessionEntity } from '@/models'
import { InjectRepository } from '@nestjs/typeorm'
import { Repository } from 'typeorm'
import { CoreService } from '@/core.service'

@Injectable()
export class VisionService {
  private readonly logger = new Logger(VisionService.name)

  constructor(
    @InjectRepository(SessionEntity)
    private readonly sessionRepository: Repository<SessionEntity>,
    private readonly coreService: CoreService,
  ) {}

  async listMedias(sessionId: string): Promise<string[]> {
    const session = await this.sessionRepository.findOneBy({
      id: sessionId,
    })
    if (!session) throw new Error('Unrecognized token.')

    const medias: string[] = [sessionId]
    this.logger.log(`listMedias: token: ${medias}`)
    return medias
  }

  async success(sessionId: string): Promise<void> {
    const session = await this.sessionRepository.findOneBy({
      id: sessionId,
    })
    if (!session) throw new Error('Unrecognized token.')

    await this.coreService.handleStateInput('success', session)
  }

  async failure(sessionId: string): Promise<void> {
    const session = await this.sessionRepository.findOneBy({
      id: sessionId,
    })
    if (!session) throw new Error('Unrecognized token.')

    this.logger.debug(`vision failure with id: ${sessionId}, sending retry...`)
    await this.coreService.handleStateInput('failure', session)
  }
}
