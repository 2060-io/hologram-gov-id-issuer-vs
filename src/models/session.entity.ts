// session.entity.ts
import { Entity, Column, PrimaryGeneratedColumn, CreateDateColumn, UpdateDateColumn } from 'typeorm'
import { StateStep } from '../common'

@Entity('session')
export class SessionEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string

  @Column({ type: 'varchar', nullable: false })
  connectionId: string

  @Column({ type: 'varchar', length: 10, nullable: true })
  lang?: string

  @Column({
    type: 'enum',
    enum: StateStep,
  })
  state?: StateStep

  @Column({ name: 'user_agent', type: 'varchar', nullable: true })
  userAgent: string

  @Column({ name: 'tracking_parameter', type: 'varchar', nullable: true })
  tp: string

  @Column({ name: 'nfc_support', type: 'boolean', nullable: true })
  nfcSupport: boolean

  @Column('jsonb', { nullable: true })
  credential_metadata?: Record<string, any>

  @CreateDateColumn()
  createdTs?: Date

  @UpdateDateColumn()
  updatedTs?: Date

  /**
   * More params...
   */
}
