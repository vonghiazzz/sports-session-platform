import { Link, Route, Routes } from 'react-router-dom'
import './App.css'
import { LiveSessionPage } from './features/live-session/LiveSessionPage'
import { PlayerDetailPage } from './features/player-management/PlayerDetailPage'
import { PlayerListPage } from './features/player-management/PlayerListPage'
import { SessionSetupPage } from './features/session-setup/SessionSetupPage'

function HomePage() {
  return (
    <main className="route-message">
      <p className="eyebrow">Nền tảng Phiên thể thao</p>
      <h1>Vận hành phiên chơi</h1>
      <p>Mở một phiên để vào phòng điều hành trực tiếp.</p>
      <div className="home-actions">
        <Link className="home-primary-link" to="/sessions/new">
          Tạo phiên mới
        </Link>
        <Link to="/players">Quản lý người chơi</Link>
      </div>
    </main>
  )
}

function NotFoundPage() {
  return (
    <main className="route-message">
      <p className="eyebrow">Nền tảng Phiên thể thao</p>
      <h1>Không tìm thấy trang</h1>
      <p>Trang bạn yêu cầu không tồn tại.</p>
      <Link to="/">Về trang chủ</Link>
    </main>
  )
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/sessions/new" element={<SessionSetupPage />} />
      <Route path="/sessions/:sessionId" element={<LiveSessionPage />} />
      <Route path="/players" element={<PlayerListPage />} />
      <Route path="/players/:playerId" element={<PlayerDetailPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
