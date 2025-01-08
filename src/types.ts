interface StatEnum {
  index: number
  value?: string
  label?: string
  description?: string
}

interface StatEvent {
  statClass: string
  entityId: string
  enums: StatEnum[]
  ts: Date
  increment: number
  doubleIncrement?: number
}
