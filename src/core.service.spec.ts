import { CoreService } from '@/core.service'
import { Cmd, StateStep } from '@/common'
import { SessionEntity } from '@/models'

// vs-agent-model (loaded by the nestjs client) requires ESM-only Credo packages that Jest cannot load
jest.mock('@verana-labs/vs-agent-model', () => ({ StatEnum: {}, StatEvent: class {} }))

const fetchMock = jest.fn()
global.fetch = fetchMock as unknown as typeof fetch

const CONNECTION_ID = 'conn-1'
const MRZ = 'P<FRAERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<' + 'L898902C36FRA7408122F1204159ZE184226B<<<<<10'

let eventCounter = 0
const envelope = (type: string, data: Record<string, unknown>) => ({
  id: `evt-${++eventCounter}`,
  type,
  timestamp: new Date().toISOString(),
  data: { connectionId: CONNECTION_ID, ...data },
})

function buildService() {
  const sessions = new Map<string, SessionEntity>()
  const sessionRepository = {
    findOneBy: jest.fn(async ({ connectionId, id }) =>
      [...sessions.values()].find(s => (connectionId ? s.connectionId === connectionId : s.id === id)),
    ),
    create: jest.fn((values: Partial<SessionEntity>) => ({ id: 'session-1', ...values }) as SessionEntity),
    save: jest.fn(async (session: SessionEntity) => {
      session.updatedTs = new Date()
      sessions.set(session.connectionId, session)
      return session
    }),
  }
  const peerRepository = { create: jest.fn(values => values), save: jest.fn(async peer => peer) }
  const connRepository = {
    findOneBy: jest.fn(async ({ id }) => ({ id, userProfile: { preferredLanguage: 'en' } })),
  }
  const credentialService = {
    createCredentialDefinition: jest.fn(async () => ({ id: 'cred-def-1' })),
    issue: jest.fn(async () => ({ credentialExchangeId: 'cx-1' })),
  }
  const i18n = { t: jest.fn((key: string) => key) }
  const config: Record<string, unknown> = {
    'appConfig.publicBaseUrl': 'https://issuer.example',
    'appConfig.webRtcServerUrl': 'https://webrtc.example',
    'appConfig.verificationTimeout': 900,
    'appConfig.jsonSchemaCredentialId': 'https://ecosystem.example/vt/schemas-passport-jsc.json',
  }
  const configService = { get: jest.fn((key: string) => config[key]) }
  const didcomm = {
    sendBasicMessage: jest.fn(async () => ({ id: 'm' })),
    sendMenu: jest.fn(async () => ({ id: 'm' })),
    sendQuestion: jest.fn(async () => ({ id: 'm' })),
    requestMrz: jest.fn(async () => ({ id: 'm' })),
    requestEmrtdData: jest.fn(async () => ({ id: 'm' })),
    offerCall: jest.fn(async () => ({ id: 'm' })),
  }

  const service = new CoreService(
    sessionRepository as any,
    peerRepository as any,
    connRepository as any,
    credentialService as any,
    i18n as any,
    configService as any,
    { didcomm } as any,
  )
  const texts = () =>
    didcomm.sendBasicMessage.mock.calls.map((call: unknown[]) => (call[0] as { content: string }).content)
  const setState = async (state: StateStep, values: Partial<SessionEntity> = {}) => {
    const session = sessionRepository.create({ connectionId: CONNECTION_ID, state, ...values })
    await sessionRepository.save(session)
  }
  const session = () => sessions.get(CONNECTION_ID)

  return { service, didcomm, credentialService, texts, setState, session }
}

describe('CoreService (VS Agent v2 events)', () => {
  beforeEach(() => {
    ;(CoreService as any).credentialDefinition = undefined
    fetchMock.mockReset()
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({ protocol: '2060-mediasoup-v1', roomId: 'room-1', wsUrl: 'wss://webrtc.example' }),
    })
  })

  it('welcomes a new connection and requests the MRZ', async () => {
    const { service, didcomm, texts, session } = buildService()

    await service.newConnection(CONNECTION_ID)

    expect(texts()).toEqual(['msg.WELCOME', 'msg.MRZ_REQUEST'])
    expect(didcomm.requestMrz).toHaveBeenCalledWith({ connectionId: CONNECTION_ID })
    expect(didcomm.sendMenu).toHaveBeenLastCalledWith({
      connectionId: CONNECTION_ID,
      menu: {
        title: 'msg.ROOT_TITTLE',
        description: '',
        options: [{ name: Cmd.ABORT, title: 'msg.CMD.ABORT', description: '' }],
      },
    })
    expect(session().state).toBe(StateStep.MRZ)
  })

  it('answers text with help and starts a verification from the contextual menu', async () => {
    const { service, didcomm, texts, setState } = buildService()
    await setState(StateStep.START)

    await service.onEvent(envelope('didcomm.basic-messages.message-received', { content: 'hello' }) as any)
    expect(texts()).toEqual(['msg.HELP'])

    await service.onEvent(envelope('didcomm.action-menu.perform-received', { name: Cmd.CREATE }) as any)
    expect(didcomm.requestMrz).toHaveBeenCalledTimes(1)
  })

  it('requests the eMRTD data once the MRZ is received', async () => {
    const { service, didcomm, texts, setState, session } = buildService()
    await setState(StateStep.MRZ)

    await service.onEvent(
      envelope('didcomm.mrtd.mrz-data-received', {
        threadId: 'thread-1',
        mrzData: { raw: MRZ, parsed: { valid: true, fields: {} } },
      }) as any,
    )

    expect(texts()).toEqual(['msg.MRZ_SUCCESSFUL', 'msg.EMRTD_REQUEST'])
    expect(didcomm.requestEmrtdData).toHaveBeenCalledWith({ connectionId: CONNECTION_ID })
    expect(session().state).toBe(StateStep.EMRTD)
  })

  it('reports a refused MRZ read and offers a retry', async () => {
    const { service, didcomm, texts, setState } = buildService()
    await setState(StateStep.MRZ)

    await service.onEvent(
      envelope('didcomm.mrtd.problem-report-received', { threadId: 't', reason: 'e.p.mrz-refused' }) as any,
    )

    expect(texts()).toEqual(['msg.MRTD_FAILED'])
    expect(didcomm.sendQuestion).toHaveBeenCalledWith({
      connectionId: CONNECTION_ID,
      question: 'msg.MENU_SELECT.PROMPT.VERIFICATION',
      validResponses: [{ text: 'msg.MENU_SELECT.OPTIONS.YES' }, { text: 'msg.MENU_SELECT.OPTIONS.NO' }],
    })
  })

  it('turns the eMRTD data into claims and offers the liveness call', async () => {
    const { service, didcomm, texts, setState, session } = buildService()
    await setState(StateStep.EMRTD)

    await service.onEvent(
      envelope('didcomm.mrtd.emrtd-data-received', {
        threadId: 't',
        dataGroups: {
          raw: {},
          parsed: {
            valid: true,
            fields: {
              mrzData: MRZ,
              images: [{ imageType: 0, imageData: { type: 'Buffer', data: [1, 2, 3] } }],
            },
          },
          verification: { authenticity: true, integrity: true },
        },
      }) as any,
    )

    expect(texts()).toEqual(['msg.EMRTD_SUCCESSFUL'])
    expect(session().state).toBe(StateStep.VERIFICATION)
    expect(session().credentialClaims).toMatchObject({
      documentType: 'TD3',
      documentNumber: 'L898902C3',
      documentIssuingState: 'FR',
      nationality: 'FR',
      firstName: 'ANNA MARIA',
      lastName: 'ERIKSSON',
      sex: 'F',
      birthDate: '19740812',
      documentExpirationDate: '20120415',
      facePhoto: 'data:image/jpeg;base64,AQID',
      mrzData: MRZ,
    })
    expect(didcomm.offerCall).toHaveBeenCalledWith(
      expect.objectContaining({
        connectionId: CONNECTION_ID,
        callType: 'service',
        description: 'msg.FACE_REQUEST',
        parameters: expect.objectContaining({ roomId: 'room-1', wsUrl: 'wss://webrtc.example' }),
      }),
    )
  })

  it('stops on an eMRTD read that fails verification', async () => {
    const { service, didcomm, texts, setState } = buildService()
    await setState(StateStep.EMRTD)

    await service.onEvent(
      envelope('didcomm.mrtd.emrtd-data-received', {
        threadId: 't',
        dataGroups: {
          raw: {},
          parsed: { valid: true, fields: { mrzData: MRZ } },
          verification: { authenticity: false, integrity: true },
        },
      }) as any,
    )

    expect(texts()).toEqual(['msg.EMRTD_VERIFICATION_FAILED'])
    expect(didcomm.offerCall).not.toHaveBeenCalled()
  })

  it('maps a question answer back to the retry option', async () => {
    const { service, didcomm, setState } = buildService()
    await setState(StateStep.VERIFICATION)

    await service.onEvent(
      envelope('didcomm.question-answer.answer-received', {
        threadId: 't',
        response: 'msg.MENU_SELECT.OPTIONS.YES',
      }) as any,
    )

    expect(didcomm.offerCall).toHaveBeenCalledTimes(1)
  })

  it('issues the credential with the credential definition of the configured VTJSC', async () => {
    const { service, credentialService, texts, setState, session } = buildService()
    const claims = { firstName: 'ANNA MARIA' }
    await setState(StateStep.VERIFICATION, { credentialClaims: claims, mrzData: MRZ })

    await service.handleStateInput('success', session())

    expect(credentialService.createCredentialDefinition).toHaveBeenCalledWith(
      'https://ecosystem.example/vt/schemas-passport-jsc.json',
      { supportRevocation: true },
    )
    expect(credentialService.issue).toHaveBeenCalledWith(claims, {
      connectionId: CONNECTION_ID,
      refId: MRZ,
      revokeIfAlreadyIssued: true,
      credentialDefinitionId: 'cred-def-1',
    })
    expect(texts()).toEqual(['msg.VERIFICATION_SUCCESSFUL', 'msg.CREDENTIAL_OFFER'])
    expect(session().state).toBe(StateStep.ISSUE)
  })

  it('looks up or creates the credential definition once for every instance', async () => {
    const first = buildService()
    const second = buildService()

    await Promise.all([first.service.onModuleInit(), second.service.onModuleInit()])

    expect(first.credentialService.createCredentialDefinition).toHaveBeenCalledTimes(1)
    expect(second.credentialService.createCredentialDefinition).not.toHaveBeenCalled()
  })

  it('completes the flow when the holder accepts the credential', async () => {
    const { service, texts, setState, session } = buildService()
    await setState(StateStep.ISSUE)

    await service.onEvent(
      envelope('didcomm.credential-exchanges.state-updated', { role: 'issuer', state: 'offer-sent' }) as any,
    )
    await service.onEvent(
      envelope('didcomm.credential-exchanges.state-updated', { role: 'holder', state: 'done' }) as any,
    )
    expect(texts()).toEqual([])

    await service.onEvent(
      envelope('didcomm.credential-exchanges.state-updated', { role: 'issuer', state: 'done' }) as any,
    )
    expect(texts()).toEqual(['msg.CREDENTIAL_ACCEPTED', 'msg.NEW_CREDENTIAL'])
    expect(session().state).toBe(StateStep.START)
  })

  it('aborts when the holder declines the credential', async () => {
    const { service, texts, setState, session } = buildService()
    await setState(StateStep.ISSUE)

    await service.onEvent(
      envelope('didcomm.credential-exchanges.state-updated', { role: 'issuer', state: 'declined' }) as any,
    )

    expect(texts()).toEqual(['msg.CREDENTIAL_REJECTED', 'msg.ABORT_PROCESS'])
    expect(session().state).toBe(StateStep.START)
  })

  it('ignores events that are not part of the flow', async () => {
    const { service, didcomm, setState } = buildService()
    await setState(StateStep.START)

    await service.onEvent(envelope('didcomm.receipts.message-receipts-received', { receipts: [] }) as any)
    await service.onEvent(envelope('some.unknown.event', {}) as any)

    expect(didcomm.sendBasicMessage).not.toHaveBeenCalled()
    expect(didcomm.sendMenu).not.toHaveBeenCalled()
  })
})
