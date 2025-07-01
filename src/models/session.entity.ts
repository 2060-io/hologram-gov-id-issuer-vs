// session.entity.ts
import { Entity, Column, PrimaryGeneratedColumn, CreateDateColumn, UpdateDateColumn } from 'typeorm'
import { StateStep } from '@/common'

@Entity('session')
export class SessionEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string

  @Column({ name: 'connection_id', type: 'varchar', nullable: false })
  connectionId: string

  @Column({ name: 'thread_id', type: 'varchar', nullable: true })
  threadId: string

  @Column({ type: 'varchar', length: 10, nullable: true, default: 'en' })
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

  @Column('jsonb', { name: 'credential_metadata', nullable: true })
  credentialClaims?: Record<string, any>

  @Column({ type: 'varchar', nullable: true })
  mrzData?: string

  @CreateDateColumn({ name: 'created_ts' })
  createdTs?: Date

  @UpdateDateColumn({ name: 'updated_ts' })
  updatedTs?: Date

  /**
   * More params...
   */
}
