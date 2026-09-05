import { useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { SkillLevel } from '../../api/contracts'
import {
  matchFormatLabel,
  skillLevelLabel,
  sportLabel,
} from '../../lib/presentation'
import {
  formatSetupLocalDateTime,
  validateSessionSetupDraft,
} from './sessionSetupModel'
import './SessionSetupPage.css'
import { useSessionSetupData } from './useSessionSetupData'
import { useSessionSetupExecution } from './useSessionSetupExecution'

const SKILL_LEVELS: readonly SkillLevel[] = [
  'WEAK',
  'WEAK_PLUS',
  'INTERMEDIATE_MINUS',
  'INTERMEDIATE',
  'INTERMEDIATE_PLUS',
  'GOOD',
]

function toggleSelection(current: readonly string[], id: string): string[] {
  return current.includes(id)
    ? current.filter((currentId) => currentId !== id)
    : [...current, id]
}

export function SessionSetupPage() {
  const [title, setTitle] = useState('')
  const [date, setDate] = useState('')
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [venueId, setVenueId] = useState('')
  const [selectedCourtIds, setSelectedCourtIds] = useState<string[]>([])
  const [selectedPlayerIds, setSelectedPlayerIds] = useState<string[]>([])
  const [playerSearch, setPlayerSearch] = useState('')
  const [validationErrors, setValidationErrors] = useState<readonly string[]>([])

  const [showVenueForm, setShowVenueForm] = useState(false)
  const [venueName, setVenueName] = useState('')
  const [venueLocation, setVenueLocation] = useState('')
  const [showCourtForm, setShowCourtForm] = useState(false)
  const [courtName, setCourtName] = useState('')
  const [showPlayerForm, setShowPlayerForm] = useState(false)
  const [playerName, setPlayerName] = useState('')
  const [skillLevel, setSkillLevel] = useState<SkillLevel>('INTERMEDIATE')

  const data = useSessionSetupData(venueId)
  const execution = useSessionSetupExecution()
  const draftLocked = execution.sessionId !== null || execution.unknownCreateOutcome

  const selectedVenue = data.venues.find((venue) => venue.id === venueId)
  const eligibleCourts = data.courts.filter(
    (court) => court.active && court.sport === 'BADMINTON',
  )
  const badmintonPlayers = data.players.filter((player) =>
    player.sportProfiles.some((profile) => profile.sport === 'BADMINTON'),
  )
  const normalizedSearch = playerSearch.trim().toLocaleLowerCase('vi-VN')
  const visiblePlayers = useMemo(
    () =>
      badmintonPlayers.filter(
        (player) =>
          normalizedSearch.length === 0 ||
          player.displayName
            .toLocaleLowerCase('vi-VN')
            .includes(normalizedSearch),
      ),
    [badmintonPlayers, normalizedSearch],
  )
  const selectedCourts = eligibleCourts.filter((court) =>
    selectedCourtIds.includes(court.id),
  )
  const selectedPlayers = badmintonPlayers.filter((player) =>
    selectedPlayerIds.includes(player.id),
  )

  async function handleCreateVenue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = venueName.trim()
    if (name.length === 0 || data.venueCreationPending) {
      return
    }
    const created = await data.createSetupVenue({
      name,
      locationText: venueLocation.trim() || null,
      active: true,
    })
    if (created !== null) {
      setVenueId(created.id)
      setSelectedCourtIds([])
      setVenueName('')
      setVenueLocation('')
      setShowVenueForm(false)
    }
  }

  async function handleCreateCourt(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = courtName.trim()
    if (
      name.length === 0 ||
      venueId.length === 0 ||
      data.courtCreationPending
    ) {
      return
    }
    const created = await data.createSetupCourt(venueId, {
      name,
      sport: 'BADMINTON',
      active: true,
    })
    if (created !== null) {
      setSelectedCourtIds((current) =>
        current.includes(created.id) ? current : [...current, created.id],
      )
      setCourtName('')
      setShowCourtForm(false)
    }
  }

  async function handleCreatePlayer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const displayName = playerName.trim()
    if (displayName.length === 0 || data.playerCreationPending) {
      return
    }
    const created = await data.createSetupPlayer({
      displayName,
      sport: 'BADMINTON',
      skillLevel,
    })
    if (created !== null) {
      setSelectedPlayerIds((current) =>
        current.includes(created.id) ? current : [...current, created.id],
      )
      setPlayerName('')
      setShowPlayerForm(false)
    }
  }

  function handleExecute() {
    const validation = validateSessionSetupDraft({
      title,
      date,
      startTime,
      endTime,
      venueId,
      courtIds: selectedCourtIds,
      playerIds: selectedPlayerIds,
    })
    setValidationErrors(validation.errors)
    if (validation.request !== null) {
      execution.execute({
        session: validation.request,
        courtIds: selectedCourtIds,
        playerIds: selectedPlayerIds,
      })
    }
  }

  return (
    <main className="session-setup">
      <header className="setup-header">
        <div>
          <p className="eyebrow">Thiết lập phiên</p>
          <h1>Tạo phiên mới</h1>
          <p>
            Chuẩn bị địa điểm, sân và người chơi trước khi vào phòng điều hành.
          </p>
        </div>
        <Link to="/">Về trang chủ</Link>
      </header>

      <section className="setup-panel" aria-labelledby="session-info-title">
        <div className="setup-section-title">
          <span>1</span>
          <div>
            <h2 id="session-info-title">Thông tin phiên</h2>
            <p>Thời gian nhập theo múi giờ Việt Nam.</p>
          </div>
        </div>
        <div className="setup-form-grid">
          <label className="setup-field setup-field-wide">
            <span>Tiêu đề phiên</span>
            <input
              value={title}
              maxLength={160}
              disabled={draftLocked}
              placeholder="Ví dụ: Cầu lông tối thứ Bảy"
              onChange={(event) => setTitle(event.target.value)}
            />
          </label>
          <label className="setup-field">
            <span>Ngày</span>
            <input
              type="date"
              value={date}
              disabled={draftLocked}
              onChange={(event) => setDate(event.target.value)}
            />
          </label>
          <label className="setup-field">
            <span>Giờ bắt đầu</span>
            <input
              type="time"
              value={startTime}
              disabled={draftLocked}
              onChange={(event) => setStartTime(event.target.value)}
            />
          </label>
          <label className="setup-field">
            <span>Giờ kết thúc</span>
            <input
              type="time"
              value={endTime}
              disabled={draftLocked}
              onChange={(event) => setEndTime(event.target.value)}
            />
          </label>
        </div>
        <div className="fixed-values" aria-label="Môn và hình thức thi đấu">
          <span>{sportLabel('BADMINTON')}</span>
          <span>{matchFormatLabel('DOUBLES')}</span>
        </div>
      </section>

      <section className="setup-panel" aria-labelledby="venue-title">
        <div className="setup-section-title">
          <span>2</span>
          <div>
            <h2 id="venue-title">Địa điểm</h2>
            <p>Chọn một địa điểm đang hoạt động.</p>
          </div>
        </div>
        <label className="setup-field">
          <span>Chọn địa điểm</span>
          <select
            value={venueId}
            disabled={draftLocked || data.venuesLoading}
            onChange={(event) => {
              setVenueId(event.target.value)
              setSelectedCourtIds([])
              setShowCourtForm(false)
              data.clearIssue()
            }}
          >
            <option value="">Chọn địa điểm</option>
            {data.venues.map((venue) => (
              <option key={venue.id} value={venue.id} disabled={!venue.active}>
                {venue.name}{venue.locationText ? ` — ${venue.locationText}` : ''}
                {!venue.active ? ' (ngừng hoạt động)' : ''}
              </option>
            ))}
          </select>
        </label>
        {data.venuesLoading && <p className="setup-note">Đang tải địa điểm…</p>}
        {data.venuesError && (
          <p className="setup-error">Không thể tải danh sách địa điểm.</p>
        )}
        {!draftLocked && (
          <button
            className="setup-link-button"
            type="button"
            onClick={() => {
              setShowVenueForm((current) => !current)
              data.clearIssue()
            }}
          >
            + Tạo địa điểm mới
          </button>
        )}
        {showVenueForm && !draftLocked && (
          <form className="inline-create-form" onSubmit={handleCreateVenue}>
            <label className="setup-field">
              <span>Tên địa điểm</span>
              <input
                value={venueName}
                maxLength={120}
                required
                onChange={(event) => {
                  setVenueName(event.target.value)
                  data.clearIssue()
                }}
              />
            </label>
            <label className="setup-field">
              <span>Địa chỉ hoặc mô tả (không bắt buộc)</span>
              <input
                value={venueLocation}
                maxLength={500}
                onChange={(event) => {
                  setVenueLocation(event.target.value)
                  data.clearIssue()
                }}
              />
            </label>
            <button
              className="secondary-setup-button"
              disabled={
                data.venueCreationPending ||
                (data.issue?.kind === 'venue' && data.issue.unknownOutcome)
              }
            >
              {data.venueCreationPending ? 'Đang tạo địa điểm…' : 'Tạo địa điểm'}
            </button>
          </form>
        )}
        {data.issue?.kind === 'venue' && (
          <p className="setup-error" role="alert">{data.issue.message}</p>
        )}
      </section>

      <section className="setup-panel" aria-labelledby="courts-title">
        <div className="setup-section-title">
          <span>3</span>
          <div>
            <h2 id="courts-title">Sân</h2>
            <p>Chọn nhiều sân cầu lông cho phiên.</p>
          </div>
        </div>
        {venueId.length === 0 ? (
          <p className="setup-note">Hãy chọn địa điểm trước.</p>
        ) : data.courtsLoading ? (
          <p className="setup-note">Đang tải sân…</p>
        ) : data.courtsError ? (
          <p className="setup-error">Không thể tải danh sách sân.</p>
        ) : eligibleCourts.length === 0 ? (
          <p className="setup-note">Địa điểm này chưa có sân cầu lông phù hợp.</p>
        ) : (
          <div className="choice-grid">
            {eligibleCourts.map((court) => (
              <label className="choice-card" key={court.id}>
                <input
                  type="checkbox"
                  checked={selectedCourtIds.includes(court.id)}
                  disabled={draftLocked}
                  onChange={() =>
                    setSelectedCourtIds((current) =>
                      toggleSelection(current, court.id),
                    )
                  }
                />
                <span>{court.name}</span>
              </label>
            ))}
          </div>
        )}
        {venueId.length > 0 && selectedVenue?.active && !draftLocked && (
          <button
            className="setup-link-button"
            type="button"
            onClick={() => {
              setShowCourtForm((current) => !current)
              data.clearIssue()
            }}
          >
            + Thêm sân
          </button>
        )}
        {showCourtForm && venueId.length > 0 && !draftLocked && (
          <form className="inline-create-form compact-form" onSubmit={handleCreateCourt}>
            <label className="setup-field">
              <span>Tên sân</span>
              <input
                value={courtName}
                maxLength={120}
                required
                onChange={(event) => {
                  setCourtName(event.target.value)
                  data.clearIssue()
                }}
              />
            </label>
            <button
              className="secondary-setup-button"
              disabled={
                data.courtCreationPending ||
                (data.issue?.kind === 'court' && data.issue.unknownOutcome)
              }
            >
              {data.courtCreationPending ? 'Đang thêm sân…' : 'Thêm sân'}
            </button>
          </form>
        )}
        {data.issue?.kind === 'court' && (
          <p className="setup-error" role="alert">{data.issue.message}</p>
        )}
      </section>

      <section className="setup-panel" aria-labelledby="players-title">
        <div className="setup-section-title">
          <span>4</span>
          <div>
            <h2 id="players-title">Người chơi</h2>
            <p>Đã chọn {selectedPlayerIds.length} người chơi.</p>
          </div>
        </div>
        <label className="setup-field">
          <span>Tìm người chơi</span>
          <input
            type="search"
            value={playerSearch}
            placeholder="Nhập tên người chơi"
            disabled={draftLocked}
            onChange={(event) => setPlayerSearch(event.target.value)}
          />
        </label>
        {data.playersLoading ? (
          <p className="setup-note">Đang tải người chơi…</p>
        ) : data.playersError ? (
          <p className="setup-error">Không thể tải danh sách người chơi.</p>
        ) : (
          <div className="player-choice-list">
            {visiblePlayers.map((player) => {
              const profile = player.sportProfiles.find(
                (candidate) => candidate.sport === 'BADMINTON',
              )
              return (
                <label className="choice-card" key={player.id}>
                  <input
                    type="checkbox"
                    checked={selectedPlayerIds.includes(player.id)}
                    disabled={draftLocked}
                    onChange={() =>
                      setSelectedPlayerIds((current) =>
                        toggleSelection(current, player.id),
                      )
                    }
                  />
                  <span>{player.displayName}</span>
                  <small>{profile ? skillLevelLabel(profile.skillLevel) : '—'}</small>
                </label>
              )
            })}
            {visiblePlayers.length === 0 && !data.playersLoading && (
              <p className="setup-note">Không tìm thấy người chơi phù hợp.</p>
            )}
          </div>
        )}
        {!draftLocked && (
          <button
            className="setup-link-button"
            type="button"
            onClick={() => {
              setShowPlayerForm((current) => !current)
              setPlayerName(playerSearch.trim())
              data.clearIssue()
            }}
          >
            + Tạo người chơi mới
          </button>
        )}
        {showPlayerForm && !draftLocked && (
          <form className="inline-create-form" onSubmit={handleCreatePlayer}>
            <label className="setup-field">
              <span>Tên hiển thị</span>
              <input
                value={playerName}
                maxLength={120}
                required
                onChange={(event) => {
                  setPlayerName(event.target.value)
                  data.clearIssue()
                }}
              />
            </label>
            <label className="setup-field">
              <span>Trình độ</span>
              <select
                value={skillLevel}
                onChange={(event) => {
                  setSkillLevel(event.target.value as SkillLevel)
                  data.clearIssue()
                }}
              >
                {SKILL_LEVELS.map((level) => (
                  <option key={level} value={level}>
                    {skillLevelLabel(level)}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="secondary-setup-button"
              disabled={
                data.playerCreationPending ||
                (data.issue?.kind === 'player' && data.issue.unknownOutcome)
              }
            >
              {data.playerCreationPending
                ? 'Đang tạo người chơi…'
                : 'Tạo người chơi'}
            </button>
          </form>
        )}
        {data.issue?.kind === 'player' && (
          <p className="setup-error" role="alert">{data.issue.message}</p>
        )}
      </section>

      <section className="setup-panel setup-review" aria-labelledby="review-title">
        <div className="setup-section-title">
          <span>5</span>
          <div>
            <h2 id="review-title">Xem lại</h2>
            <p>Đây có đúng là phiên bạn sắp tạo không?</p>
          </div>
        </div>
        <dl className="review-details">
          <div><dt>Tiêu đề</dt><dd>{title.trim() || '—'}</dd></div>
          <div>
            <dt>Thời gian Việt Nam</dt>
            <dd>
              {formatSetupLocalDateTime(date, startTime)} –{' '}
              {endTime || '—'}
            </dd>
          </div>
          <div><dt>Địa điểm</dt><dd>{selectedVenue?.name ?? '—'}</dd></div>
          <div>
            <dt>Sân</dt>
            <dd>{selectedCourts.map((court) => court.name).join(', ') || '—'}</dd>
          </div>
          <div>
            <dt>Người chơi ({selectedPlayers.length})</dt>
            <dd>
              {selectedPlayers.map((player) => player.displayName).join(', ') || '—'}
            </dd>
          </div>
        </dl>

        {validationErrors.length > 0 && (
          <div className="validation-summary" role="alert">
            <strong>Hãy hoàn tất thông tin:</strong>
            <ul>{validationErrors.map((error) => <li key={error}>{error}</li>)}</ul>
          </div>
        )}
        {execution.sessionId !== null && (
          <p className="partial-setup-note">
            Phiên đã được tạo. Các lần tiếp tục sẽ kiểm tra dữ liệu đã lưu và chỉ
            thêm phần còn thiếu.
          </p>
        )}
        {execution.errorMessage && (
          <p className="setup-error" role="alert">{execution.errorMessage}</p>
        )}
        {execution.progressMessage && (
          <p className="setup-progress" role="status">{execution.progressMessage}</p>
        )}
        <button
          className="primary-setup-button"
          type="button"
          disabled={execution.isPending || execution.unknownCreateOutcome}
          onClick={handleExecute}
        >
          {execution.isPending
            ? execution.progressMessage ?? 'Đang tạo phiên…'
            : execution.sessionId !== null
              ? 'Tiếp tục thiết lập'
              : 'Tạo và bắt đầu phiên'}
        </button>
      </section>
    </main>
  )
}
