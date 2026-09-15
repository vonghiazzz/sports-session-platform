import { Link } from 'react-router-dom'
import type { SessionStatus } from '../../api/contracts'
import { formatVietnamDateTime, statusLabel } from '../../lib/presentation'
import { useSessionDiscovery } from './useSessionDiscovery'
import './SessionDiscoveryList.css'

function openActionLabel(status: SessionStatus): string {
  if (status === 'IN_PROGRESS') {
    return 'Mở Control Room'
  }
  if (status === 'PLANNED') {
    return 'Mở phiên'
  }
  return 'Xem phiên'
}

export function SessionDiscoveryList() {
  const sessionsQuery = useSessionDiscovery()

  return (
    <section className="session-discovery" aria-labelledby="session-discovery-title">
      <div className="session-discovery-heading">
        <div>
          <p className="eyebrow">Quản lý phiên</p>
          <h2 id="session-discovery-title">Phiên gần đây</h2>
        </div>
        {sessionsQuery.isFetching && !sessionsQuery.isPending ? (
          <span role="status">Đang cập nhật…</span>
        ) : null}
      </div>

      {sessionsQuery.isPending ? (
        <p className="session-discovery-state" role="status">
          Đang tải danh sách phiên…
        </p>
      ) : sessionsQuery.isError ? (
        <div className="session-discovery-state session-discovery-error" role="alert">
          <p>Không thể tải danh sách phiên.</p>
          <button type="button" onClick={() => void sessionsQuery.refetch()}>
            Thử lại
          </button>
        </div>
      ) : sessionsQuery.data.length === 0 ? (
        <div className="session-discovery-state">
          <p>Chưa có phiên nào.</p>
          <Link to="/sessions/new">Tạo phiên mới</Link>
        </div>
      ) : (
        <div className="session-discovery-list">
          {sessionsQuery.data.map((session) => (
            <article className="session-discovery-card" key={session.id}>
              <div>
                <span className={`session-status session-status-${session.status.toLowerCase()}`}>
                  {statusLabel(session.status)}
                </span>
                <h3>{session.title}</h3>
                <time dateTime={session.plannedStartAt}>
                  {formatVietnamDateTime(session.plannedStartAt)}
                </time>
              </div>
              <Link to={`/sessions/${encodeURIComponent(session.id)}`}>
                {openActionLabel(session.status)}
              </Link>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
