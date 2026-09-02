import { Link, Route, Routes } from 'react-router-dom'
import './App.css'
import { LiveSessionPage } from './features/live-session/LiveSessionPage'

function HomePage() {
  return (
    <main className="route-message">
      <p className="eyebrow">Sports Session Platform</p>
      <h1>Host operations</h1>
      <p>Open a Session to view the Host Live Session Control Room.</p>
    </main>
  )
}

function NotFoundPage() {
  return (
    <main className="route-message">
      <p className="eyebrow">Sports Session Platform</p>
      <h1>Page not found</h1>
      <p>The requested page does not exist.</p>
      <Link to="/">Return home</Link>
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
