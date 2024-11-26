import { Entity, Column, PrimaryGeneratedColumn, CreateDateColumn, UpdateDateColumn } from 'typeorm'

@Entity('credential_issuance')
export class CredentialEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string

  @Column({ name: 'connection_id', type: 'varchar', nullable: true })
  connectionId: string

  @Column({ type: 'bytea', nullable: true })
  hash: Buffer

  @Column({ name: 'revocation_id', type: 'varchar', nullable: true })
  revocationId: string

  @CreateDateColumn({ name: 'created_ts' })
  createdTs?: Date

  @UpdateDateColumn({ name: 'updated_ts' })
  updatedTs?: Date
}
