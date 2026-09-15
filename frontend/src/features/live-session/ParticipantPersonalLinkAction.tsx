import { useMutation } from '@tanstack/react-query'
import { getSessionParticipantPersonalAccess } from '../../api/liveSessionApi'

function buildPlayerSessionUrl(token: string): string {
  const path = `/player-session/${encodeURIComponent(token)}`
  return new URL(path, window.location.origin).toString()
}

async function copyPlayerSessionLink(
  sessionId: string,
  sessionParticipantId: string,
): Promise<void> {
  const access = await getSessionParticipantPersonalAccess(
    sessionId,
    sessionParticipantId,
  )
  const clipboard = navigator.clipboard
  if (clipboard === undefined) {
    throw new Error('Clipboard is unavailable')
  }
  await clipboard.writeText(buildPlayerSessionUrl(access.personalAccessToken))
}

export function ParticipantPersonalLinkAction({
  sessionId,
  sessionParticipantId,
  participantLabel,
}: {
  readonly sessionId: string
  readonly sessionParticipantId: string
  readonly participantLabel: string
}) {
  const mutation = useMutation<void, Error>({
    mutationFn: () => copyPlayerSessionLink(sessionId, sessionParticipantId),
    retry: false,
  })

  return (
    <div className="personal-link-action">
      <button
        className="secondary-action-button"
        type="button"
        aria-label={`Sao chép link người chơi cho ${participantLabel}`}
        disabled={mutation.isPending}
        onClick={() => {
          mutation.reset()
          mutation.mutate()
        }}
      >
        {mutation.isPending ? 'Đang sao chép…' : 'Sao chép link người chơi'}
      </button>
      {mutation.isSuccess && (
        <p className="personal-link-feedback" role="status">
          Đã sao chép link
        </p>
      )}
      {mutation.isError && (
        <p className="action-feedback personal-link-error" role="alert">
          Không thể sao chép link. Hãy thử lại.
        </p>
      )}
    </div>
  )
}
