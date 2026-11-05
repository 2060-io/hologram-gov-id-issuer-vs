import { EmrtdDataReceivedData } from '@verana-labs/vs-agent-client'
import { parse as parseMrz } from 'mrz'

/**
 * Document data that the service derives from an eMRTD read.
 *
 * The v1 Admin API delivered these fields already processed (`dataGroups.processed`).
 * The v2 `didcomm.mrtd.emrtd-data-received` event carries the decoded data groups as
 * they come from the MRTD protocol (`dataGroups.parsed.fields`), so the service derives
 * the same fields itself, with the rules the VS Agent applied in v1.
 */
export interface ProcessedEmrtdData {
  documentType?: string
  documentNumber?: string
  issuingState?: string
  dateOfBirth?: string
  dateOfExpiry?: string
  sex?: string
  nationality?: string
  lastName?: string
  firstName?: string
  faceImages: string[]
  mrzString?: string
}

type EmrtdDataGroups = EmrtdDataReceivedData['dataGroups']

/**
 * Converts binary data, as it arrives after JSON serialization, into a Buffer.
 * A Node.js Buffer serializes as `{ type: 'Buffer', data: number[] }`, and a Uint8Array as an
 * object with numeric keys. A string is taken as base64.
 */
export function toBuffer(value: unknown): Buffer | undefined {
  if (value === null || value === undefined) return undefined
  if (Buffer.isBuffer(value)) return value
  if (value instanceof Uint8Array) return Buffer.from(value)
  if (typeof value === 'string') return Buffer.from(value, 'base64')
  if (Array.isArray(value)) return Buffer.from(value as number[])
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    if (record.type === 'Buffer' && Array.isArray(record.data)) return Buffer.from(record.data as number[])
    const keys = Object.keys(record)
    if (keys.length > 0 && keys.every(key => /^\d+$/.test(key))) {
      return Buffer.from(keys.sort((a, b) => Number(a) - Number(b)).map(key => record[key] as number))
    }
  }
  return undefined
}

/**
 * Converts an MRTD date from `YYMMDD` to `YYYYMMDD`, as the VS Agent did in v1: an expiration date
 * lies at most 10 years ahead, a birth date never lies in the future. Other input is returned as is.
 */
export function convertShortDate(
  date: string | null | undefined,
  isExpirationDate: boolean,
): string | undefined {
  if (!date || !/^\d{6}$/.test(date)) return date ?? undefined
  const currentYear = new Date().getFullYear()
  const year = Math.floor(currentYear / 100) * 100 + parseInt(date.slice(0, 2), 10)
  const limit = isExpirationDate ? currentYear + 10 : currentYear
  const fullYear = year > limit ? year - 100 : year
  return `${fullYear}${date.slice(2)}`
}

function formatMrz(mrzString: string): string {
  switch (mrzString.length) {
    case 88:
      return mrzString.slice(0, 44) + '\n' + mrzString.slice(44)
    case 72:
      return mrzString.slice(0, 36) + '\n' + mrzString.slice(36)
    case 90:
      return mrzString.slice(0, 30) + '\n' + mrzString.slice(30, 60) + '\n' + mrzString.slice(60)
    case 30:
      return mrzString
    case 18:
      return mrzString.slice(0, 9) + '\n' + mrzString.slice(9)
    default:
      throw new Error(`Unrecognized MRZ length: ${mrzString.length}`)
  }
}

/**
 * Derives the document data from the `dataGroups` of a `didcomm.mrtd.emrtd-data-received` event.
 *
 * @returns the processed data, or `undefined` when the event carries no decoded fields.
 * @throws when the MRZ of DG1 has an unrecognized length.
 */
export function processEmrtdData(dataGroups: EmrtdDataGroups): ProcessedEmrtdData | undefined {
  const fields = dataGroups?.parsed?.fields as Record<string, any> | undefined
  if (!dataGroups?.parsed?.valid || !fields || typeof fields.mrzData !== 'string') return undefined

  const mrzString: string = fields.mrzData
  const parsedMrz = parseMrz(formatMrz(mrzString))

  const images: { imageType?: number; imageData?: unknown }[] = Array.isArray(fields.images)
    ? fields.images
    : []
  const faceImages = images
    .map(image => {
      const data = toBuffer(image.imageData)
      if (!data) return undefined
      const mimeType = image.imageType === 1 ? 'image/jp2' : 'image/jpeg'
      return `data:${mimeType};base64,${data.toString('base64')}`
    })
    .filter((image): image is string => Boolean(image))

  const rawSex = parsedMrz.fields.sex ?? undefined
  const fullDateOfBirth = fields.additionalPersonalData?.fullDateOfBirth
  const dateOfBirth =
    !fullDateOfBirth || fullDateOfBirth.toString().length < 6
      ? parsedMrz.fields.birthDate
      : fullDateOfBirth.toString()

  return {
    documentType: parsedMrz.format,
    documentNumber: parsedMrz.documentNumber ?? undefined,
    issuingState: parsedMrz.fields.issuingState ?? undefined,
    dateOfExpiry: convertShortDate(parsedMrz.fields.expirationDate, true),
    sex: rawSex ? rawSex.charAt(0).toUpperCase() : undefined,
    nationality: parsedMrz.fields.nationality ?? undefined,
    lastName: parsedMrz.fields.lastName ?? undefined,
    firstName: parsedMrz.fields.firstName ?? undefined,
    faceImages,
    dateOfBirth: convertShortDate(dateOfBirth, false),
    mrzString,
  }
}
