import { processEmrtdData, toBuffer } from './emrtd'

// ICAO 9303 specimen, with FRA as issuing state and nationality
const SPECIMEN_MRZ =
  'P<FRAERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<' + 'L898902C36FRA7408122F1204159ZE184226B<<<<<10'

const face = Buffer.from([0xff, 0xd8, 0xff, 0xe0])

// What a VS Agent v2 `didcomm.mrtd.emrtd-data-received` event carries after JSON serialization
const emrtdDataGroups = (fields: Record<string, unknown> = {}) =>
  JSON.parse(
    JSON.stringify({
      raw: { DG1: 'AAAA' },
      parsed: {
        valid: true,
        fields: { mrzData: SPECIMEN_MRZ, images: [{ imageType: 0, imageData: face }], ...fields },
      },
      verification: { authenticity: true, integrity: true },
    }),
  )

describe('processEmrtdData', () => {
  it('derives the document data from the decoded data groups', () => {
    const processed = processEmrtdData(emrtdDataGroups())

    expect(processed).toEqual({
      documentType: 'TD3',
      documentNumber: 'L898902C3',
      issuingState: 'FRA',
      nationality: 'FRA',
      firstName: 'ANNA MARIA',
      lastName: 'ERIKSSON',
      sex: 'F',
      dateOfBirth: '19740812',
      dateOfExpiry: '20120415',
      faceImages: [`data:image/jpeg;base64,${face.toString('base64')}`],
      mrzString: SPECIMEN_MRZ,
    })
  })

  it('prefers the full date of birth of DG11 and marks JPEG2000 faces', () => {
    const processed = processEmrtdData(
      emrtdDataGroups({
        additionalPersonalData: { fullDateOfBirth: 19740812 },
        images: [{ imageType: 1, imageData: face }],
      }),
    )

    expect(processed.dateOfBirth).toBe('19740812')
    expect(processed.faceImages[0]).toMatch(/^data:image\/jp2;base64,/)
  })

  it('returns undefined when the agent could not decode the data groups', () => {
    expect(processEmrtdData({ raw: {}, parsed: { valid: false } })).toBeUndefined()
    expect(processEmrtdData(undefined)).toBeUndefined()
  })

  it('rejects an MRZ of unknown length', () => {
    expect(() => processEmrtdData(emrtdDataGroups({ mrzData: 'P<FRA' }))).toThrow('Unrecognized MRZ length')
  })
})

describe('toBuffer', () => {
  it('reads the serialized forms of binary data', () => {
    expect(toBuffer({ type: 'Buffer', data: [1, 2] })).toEqual(Buffer.from([1, 2]))
    expect(toBuffer({ 0: 1, 1: 2 })).toEqual(Buffer.from([1, 2]))
    expect(toBuffer([1, 2])).toEqual(Buffer.from([1, 2]))
    expect(toBuffer(Buffer.from([1, 2]).toString('base64'))).toEqual(Buffer.from([1, 2]))
    expect(toBuffer(undefined)).toBeUndefined()
  })
})
