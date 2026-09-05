import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef } from 'react'
import type {
  SessionCourtResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  checkInParticipant,
  disableSessionCourt,
  enableSessionCourt,
  leaveParticipant,
  pauseParticipant,
  resumeParticipant,
} from '../../api/liveSessionApi'

export type ParticipantAction = 'CHECK_IN' | 'PAUSE' | 'RESUME' | 'LEAVE'
export type SessionCourtAction = 'DISABLE' | 'ENABLE'

interface LiveActionState<TAction> {
  readonly execute: (action: TAction) => void
  readonly isPending: boolean
  readonly pendingAction: TAction | null
  readonly errorMessage: string | null
}

function operationErrorMessage(
  error: Error | null,
  resourceName: 'Participant' | 'Court',
): string | null {
  if (error === null) {
    return null
  }
  if (!(error instanceof HttpError)) {
    return 'Mất kết nối. Dữ liệu hiện tại đang được tải lại; hãy kiểm tra trạng thái trước khi thử lại.'
  }
  if (error.status === 409) {
    return 'Trạng thái trực tiếp đã thay đổi. Dữ liệu hiện tại đang được tải lại.'
  }
  if (error.status === 404) {
    const resourceLabel = resourceName === 'Participant' ? 'Người chơi' : 'Sân'
    return `${resourceLabel} không còn khả dụng. Dữ liệu hiện tại đang được tải lại.`
  }
  return 'Không thể hoàn tất thao tác. Hãy làm mới trạng thái hiện tại trước khi thử lại.'
}

export function useParticipantAction(
  sessionId: string,
  sessionParticipantId: string,
): LiveActionState<ParticipantAction> {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const mutation = useMutation<
    SessionParticipantResponse,
    Error,
    ParticipantAction
  >({
    mutationFn: (action) => {
      switch (action) {
        case 'CHECK_IN':
          return checkInParticipant(sessionId, sessionParticipantId)
        case 'PAUSE':
          return pauseParticipant(sessionId, sessionParticipantId)
        case 'RESUME':
          return resumeParticipant(sessionId, sessionParticipantId)
        case 'LEAVE':
          return leaveParticipant(sessionId, sessionParticipantId)
      }
    },
    retry: false,
    onSettled: async () => {
      try {
        await queryClient.invalidateQueries({
          queryKey: ['sessionParticipants', sessionId],
          exact: true,
        })
      } finally {
        inFlight.current = false
      }
    },
  })
  const { mutate } = mutation
  const execute = useCallback(
    (action: ParticipantAction) => {
      if (inFlight.current) {
        return
      }
      inFlight.current = true
      mutate(action)
    },
    [mutate],
  )

  return {
    execute,
    isPending: mutation.isPending,
    pendingAction: mutation.isPending ? mutation.variables : null,
    errorMessage: operationErrorMessage(mutation.error, 'Participant'),
  }
}

export function useSessionCourtAction(
  sessionId: string,
  sessionCourtId: string,
): LiveActionState<SessionCourtAction> {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const mutation = useMutation<SessionCourtResponse, Error, SessionCourtAction>(
    {
      mutationFn: (action) => {
        switch (action) {
          case 'DISABLE':
            return disableSessionCourt(sessionId, sessionCourtId)
          case 'ENABLE':
            return enableSessionCourt(sessionId, sessionCourtId)
        }
      },
      retry: false,
      onSettled: async () => {
        try {
          await queryClient.invalidateQueries({
            queryKey: ['sessionCourts', sessionId],
            exact: true,
          })
        } finally {
          inFlight.current = false
        }
      },
    },
  )
  const { mutate } = mutation
  const execute = useCallback(
    (action: SessionCourtAction) => {
      if (inFlight.current) {
        return
      }
      inFlight.current = true
      mutate(action)
    },
    [mutate],
  )

  return {
    execute,
    isPending: mutation.isPending,
    pendingAction: mutation.isPending ? mutation.variables : null,
    errorMessage: operationErrorMessage(mutation.error, 'Court'),
  }
}
