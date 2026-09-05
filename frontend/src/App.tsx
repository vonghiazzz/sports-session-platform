import { Link, Route, Routes } from 'react-router-dom'
import './App.css'
import { LiveSessionPage } from './features/live-session/LiveSessionPage'

function HomePage() {
  return (
    <main className="route-message">
      <p className="eyebrow">Nền tảng Phiên thể thao</p>
      <h1>Vận hành phiên chơi</h1>
      <p>Mở một phiên để vào phòng điều hành trực tiếp.</p>
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
      <Route path="/sessions/:sessionId" element={<LiveSessionPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
