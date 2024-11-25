import { EventNotificationType, PeerType } from '@/common'
import { Entity, Column, PrimaryGeneratedColumn, CreateDateColumn, UpdateDateColumn } from 'typeorm'

@Entity('peer_registry')
export class PeerEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string

  @Column({ name: 'connection_id', type: 'varchar', nullable: true })
  connectionId: string

  @Column({
    type: 'enum',
    enum: EventNotificationType,
    nullable: true,
  })
  event?: EventNotificationType

  @Column({ name: 'room_id', type: 'varchar', nullable: true })
  roomId: string

  @Column({ name: 'ws_url', type: 'varchar', nullable: true })
  wsUrl: string

  @Column({
    type: 'enum',
    enum: PeerType,
  })
  type: PeerType

  @CreateDateColumn({ name: 'created_ts' })
  createdTs?: Date

  @UpdateDateColumn({ name: 'updated_ts' })
  updatedTs?: Date
}
