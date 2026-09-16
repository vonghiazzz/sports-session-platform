import { useMutation } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { CreateSessionRequest } from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  addSessionCourt,
  addSessionParticipant,
  createSession,
  getSetupSession,
  getSetupSessionCourts,
  getSetupSessionParticipants,
  startSetupSession,
} from '../../api/sessionSetupApi'

export interface SessionSetupExecutionInput {
  readonly session: CreateSessionRequest
  readonly courtIds: readonly string[]
  readonly playerIds: readonly string[]
}

type ExecutionStage =
  | 'creating-session'
  | 'reconciling'
  | 'adding-courts'
  | 'adding-players'
  | 'starting-session'

interface ExecutionFailure {
  readonly message: string
  readonly unknownCreateOutcome: boolean
}

const STAGE_MESSAGES: Readonly<Record<ExecutionStage, string>> = {
  'creating-session': 'Đang tạo phiên…',
  reconciling: 'Đang kiểm tra thiết lập hiện tại…',
  'adding-courts': 'Đang thêm sân…',
  'adding-players': 'Đang thêm người chơi…',
  'starting-session': 'Đang bắt đầu phiên…',
}

function failureFor(stage: ExecutionStage, error: unknown): ExecutionFailure {
  if (stage === 'creating-session' && !(error instanceof HttpError)) {
    return {
      unknownCreateOutcome: true,
      message:
        'Mất kết nối khi tạo phiên nên chưa thể xác định phiên đã được tạo hay chưa. Không gửi lại để tránh tạo trùng; hãy kiểm tra hệ thống trước khi thử một thiết lập mới.',
    }
  }
  const messages: Readonly<Record<ExecutionStage, string>> = {
    'creating-session': 'Không thể tạo phiên. Hãy kiểm tra thông tin và thử lại.',
    reconciling:
      'Không thể kiểm tra trạng thái thiết lập. Phiên đã tạo được giữ nguyên; bạn có thể tiếp tục an toàn.',
    'adding-courts':
      'Không thể thêm đủ sân. Phiên và các sân đã thêm được giữ nguyên; bạn có thể tiếp tục an toàn.',
    'adding-players':
      'Không thể thêm đủ người chơi. Phiên và dữ liệu đã thêm được giữ nguyên; bạn có thể tiếp tục an toàn.',
    'starting-session':
      'Không thể xác nhận phiên đã bắt đầu. Phiên đã tạo được giữ nguyên; hãy tiếp tục để kiểm tra lại trạng thái.',
  }
  return { unknownCreateOutcome: false, message: messages[stage] }
}

export function useSessionSetupExecution() {
  const navigate = useNavigate()
  const sessionIdRef = useRef<string | null>(null)
  const inFlight = useRef(false)
  const unknownCreateOutcomeRef = useRef(false)
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [stage, setStage] = useState<ExecutionStage | null>(null)
  const [failure, setFailure] = useState<ExecutionFailure | null>(null)

  const mutation = useMutation({
    mutationFn: async (input: SessionSetupExecutionInput) => {
      let currentStage: ExecutionStage = 'creating-session'
      try {
        let currentSessionId = sessionIdRef.current
        if (currentSessionId === null) {
          setStage(currentStage)
          const created = await createSession(input.session)
          currentSessionId = created.id
          sessionIdRef.current = currentSessionId
          setSessionId(currentSessionId)
        }

        currentStage = 'reconciling'
        setStage(currentStage)
        const currentSession = await getSetupSession(currentSessionId)
        if (currentSession.status === 'IN_PROGRESS') {
          return currentSessionId
        }
        if (currentSession.status !== 'PLANNED') {
          throw new Error('Session is no longer available for setup')
        }

        const [allocatedCourts, registeredParticipants] = await Promise.all([
          getSetupSessionCourts(currentSessionId),
          getSetupSessionParticipants(currentSessionId),
        ])
        const allocatedCourtIds = new Set(
          allocatedCourts.map((court) => court.courtId),
        )
        const registeredPlayerIds = new Set(
          registeredParticipants.map((participant) => participant.playerId),
        )

        currentStage = 'adding-courts'
        setStage(currentStage)
        for (const courtId of input.courtIds) {
          if (!allocatedCourtIds.has(courtId)) {
            await addSessionCourt(currentSessionId, { courtId })
          }
        }

        currentStage = 'adding-players'
        setStage(currentStage)
        for (const playerId of input.playerIds) {
          if (!registeredPlayerIds.has(playerId)) {
            await addSessionParticipant(currentSessionId, { playerId })
          }
        }

        currentStage = 'starting-session'
        setStage(currentStage)
        try {
          await startSetupSession(currentSessionId)
        } catch (error) {
          const reconciled = await getSetupSession(currentSessionId)
          if (reconciled.status !== 'IN_PROGRESS') {
            throw error
          }
        }
        return currentSessionId
      } catch (error) {
        const nextFailure = failureFor(currentStage, error)
        unknownCreateOutcomeRef.current = nextFailure.unknownCreateOutcome
        setFailure(nextFailure)
        throw error
      }
    },
    retry: false,
    onSuccess: (createdSessionId) => {
      navigate(`/sessions/${createdSessionId}`)
    },
    onSettled: () => {
      inFlight.current = false
      setStage(null)
    },
  })

  const execute = useCallback(
    (input: SessionSetupExecutionInput) => {
      if (inFlight.current || unknownCreateOutcomeRef.current) {
        return
      }
      inFlight.current = true
      setFailure(null)
      mutation.mutate(input)
    },
    [mutation],
  )

  return {
    execute,
    isPending: mutation.isPending,
    progressMessage: stage === null ? null : STAGE_MESSAGES[stage],
    sessionId,
    errorMessage: failure?.message ?? null,
    unknownCreateOutcome: failure?.unknownCreateOutcome ?? false,
  }
}
