import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { resolvePlayerSessionAccess } from '../../api/playerSessionAccessApi'
import { HttpError } from '../../api/http'
import { PlayerSessionView } from './PlayerSessionPage'

export function PlayerSessionAccessPage() {
  const { token = '' } = useParams()
  const accessQuery = useQuery({
    queryKey: ['playerSessionAccess', token],
    queryFn: ({ signal }) => resolvePlayerSessionAccess(token, signal),
    enabled: token.length > 0,
    retry: false,
  })

  if (accessQuery.isPending) {
    return (
      <main className="player-session-state" aria-live="polite">
        <p className="eyebrow">Trạng thái trong phiên</p>
        <h1>Đang xác minh liên kết của bạn…</h1>
      </main>
    )
  }

  if (accessQuery.isError) {
    const notFound = accessQuery.error instanceof HttpError
      && accessQuery.error.status === 404
    return (
      <main className="player-session-state" role={notFound ? undefined : 'alert'}>
        <p className="eyebrow">Trạng thái trong phiên</p>
        <h1>{notFound ? 'Không tìm thấy liên kết' : 'Không thể mở liên kết'}</h1>
        <p>
          {notFound
            ? 'Liên kết bạn yêu cầu không khả dụng.'
            : 'Không thể xác minh liên kết lúc này.'}
        </p>
        {!notFound && (
          <button
            className="player-session-refresh"
            type="button"
            disabled={accessQuery.isFetching}
            onClick={() => void accessQuery.refetch()}
          >
            {accessQuery.isFetching ? 'Đang thử lại…' : 'Thử lại'}
          </button>
        )}
      </main>
    )
  }

  return (
    <PlayerSessionView
      sessionId={accessQuery.data.sessionId}
      sessionParticipantId={accessQuery.data.sessionParticipantId}
    />
  )
}
