import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import type { BuddyPairResponse } from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  createBuddyPair,
  removeBuddyPair,
} from '../../api/liveSessionApi'

type BuddyPairCommand =
  | {
      readonly type: 'create'
      readonly firstSessionParticipantId: string
      readonly secondSessionParticipantId: string
    }
  | { readonly type: 'remove'; readonly buddyPairId: string }

interface BuddyPairError {
  readonly operationKey: string
  readonly message: string
}

function commandKey(command: BuddyPairCommand): string {
  return command.type === 'create'
    ? `create:${command.firstSessionParticipantId}`
    : `remove:${command.buddyPairId}`
}

function buddyPairErrorMessage(error: unknown): string {
  if (error instanceof HttpError) {
    return error.message
  }
  return 'Mất kết nối. Hãy kiểm tra trạng thái hiện tại trước khi thử lại.'
}

export function useBuddyPairActions(sessionId: string) {
  const queryClient = useQueryClient()
  const inFlightKeys = useRef(new Set<string>())
  const [pendingKeys, setPendingKeys] = useState<ReadonlySet<string>>(
    () => new Set(),
  )
  const [error, setError] = useState<BuddyPairError | null>(null)

  const mutation = useMutation<BuddyPairResponse | void, Error, BuddyPairCommand>({
    mutationFn: (command) =>
      command.type === 'create'
        ? createBuddyPair(
            sessionId,
            command.firstSessionParticipantId,
            command.secondSessionParticipantId,
          )
        : removeBuddyPair(sessionId, command.buddyPairId),
    retry: false,
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ['sessionParticipants', sessionId],
        exact: true,
      }),
  })

  const execute = useCallback(
    async (command: BuddyPairCommand): Promise<boolean> => {
      const key = commandKey(command)
      if (inFlightKeys.current.has(key)) {
        return false
      }

      inFlightKeys.current.add(key)
      setPendingKeys((current) => new Set(current).add(key))
      setError((current) =>
        current?.operationKey === key ? null : current,
      )
      try {
        await mutation.mutateAsync(command)
        return true
      } catch (caught) {
        setError({ operationKey: key, message: buddyPairErrorMessage(caught) })
        return false
      } finally {
        inFlightKeys.current.delete(key)
        setPendingKeys((current) => {
          const next = new Set(current)
          next.delete(key)
          return next
        })
      }
    },
    [mutation],
  )

  return {
    create: (
      firstSessionParticipantId: string,
      secondSessionParticipantId: string,
    ) =>
      execute({
        type: 'create',
        firstSessionParticipantId,
        secondSessionParticipantId,
      }),
    remove: (buddyPairId: string) =>
      execute({ type: 'remove', buddyPairId }),
    isCreatePending: (sessionParticipantId: string) =>
      pendingKeys.has(`create:${sessionParticipantId}`),
    isRemovePending: (buddyPairId: string) =>
      pendingKeys.has(`remove:${buddyPairId}`),
    createError: (sessionParticipantId: string) =>
      error?.operationKey === `create:${sessionParticipantId}`
        ? error.message
        : null,
    removeError: (buddyPairId: string) =>
      error?.operationKey === `remove:${buddyPairId}` ? error.message : null,
  }
}

export type BuddyPairActions = ReturnType<typeof useBuddyPairActions>
