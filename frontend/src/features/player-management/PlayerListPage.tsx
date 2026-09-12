import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { skillLevelLabel, sportLabel } from '../../lib/presentation'
import {
  badmintonProfile,
  formatPlayerRating,
  normalizePlayerSearch,
  ratedMatchesLabel,
  ratingBasisLabel,
} from './playerManagementModel'
import { usePlayerList } from './usePlayerManagement'
import './PlayerManagement.css'

export function PlayerListPage() {
  const [searchDraft, setSearchDraft] = useState('')
  const [search, setSearch] = useState('')
  const playersQuery = usePlayerList(search)

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSearch(normalizePlayerSearch(searchDraft))
  }

  return (
    <main className="player-management">
      <header className="player-management-header">
        <div>
          <p className="eyebrow">Quản lý người chơi</p>
          <h1>Người chơi</h1>
          <p>Xem trình Host đánh giá và Rating hiện tại của từng người chơi.</p>
        </div>
        <nav aria-label="Điều hướng quản lý người chơi">
          <Link to="/sessions/new">Tạo phiên mới</Link>
          <Link to="/">Trang chủ</Link>
        </nav>
      </header>

      <section className="player-panel" aria-labelledby="player-list-title">
        <div className="player-panel-title">
          <div>
            <h2 id="player-list-title">Danh sách người chơi</h2>
            <p>Tìm kiếm sử dụng dữ liệu trực tiếp từ hệ thống.</p>
          </div>
          <form className="player-search" role="search" onSubmit={submitSearch}>
            <label htmlFor="player-search-input">Tên người chơi</label>
            <div>
              <input
                id="player-search-input"
                value={searchDraft}
                placeholder="Nhập tên cần tìm"
                onChange={(event) => setSearchDraft(event.target.value)}
              />
              <button disabled={playersQuery.isFetching}>
                {playersQuery.isFetching ? 'Đang tìm…' : 'Tìm kiếm'}
              </button>
            </div>
          </form>
        </div>

        {playersQuery.isPending ? (
          <p className="player-state">Đang tải người chơi...</p>
        ) : playersQuery.isError ? (
          <div className="player-state player-error" role="alert">
            <p>Không thể tải danh sách người chơi.</p>
            <button type="button" onClick={() => void playersQuery.refetch()}>
              Thử lại
            </button>
          </div>
        ) : playersQuery.data.length === 0 ? (
          <p className="player-state">
            {search.length === 0
              ? 'Chưa có người chơi.'
              : 'Không tìm thấy người chơi phù hợp.'}
          </p>
        ) : (
          <div className="player-table-wrap">
            <table className="player-table">
              <thead>
                <tr>
                  <th>Người chơi</th>
                  <th>Hồ sơ</th>
                  <th>Rating hiện tại</th>
                  <th aria-label="Thao tác" />
                </tr>
              </thead>
              <tbody>
                {playersQuery.data.map((player) => {
                  const profile = badmintonProfile(player.sportProfiles)
                  return (
                    <tr key={player.id}>
                      <td><strong>{player.displayName}</strong></td>
                      <td>
                        {profile === undefined ? (
                          'Chưa có hồ sơ Cầu lông'
                        ) : (
                          <>
                            <span>{sportLabel(profile.sport)}</span>
                            <small>Trình: {skillLevelLabel(profile.skillLevel)}</small>
                          </>
                        )}
                      </td>
                      <td>
                        {profile === undefined ? (
                          '—'
                        ) : (
                          <>
                            <strong>{formatPlayerRating(profile.rating.ratingValue)}</strong>
                            <small>{ratedMatchesLabel(profile.rating.ratedMatches)}</small>
                            <small>{ratingBasisLabel(profile.rating.ratingBasis)}</small>
                          </>
                        )}
                      </td>
                      <td>
                        <Link to={`/players/${encodeURIComponent(player.id)}`}>
                          Xem chi tiết
                        </Link>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  )
}
