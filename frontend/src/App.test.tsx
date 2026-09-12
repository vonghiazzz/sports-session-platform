import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

vi.mock('./features/player-management/PlayerListPage', () => ({
  PlayerListPage: () => <h1>Danh sách quản lý người chơi</h1>,
}))

vi.mock('./features/player-management/PlayerDetailPage', () => ({
  PlayerDetailPage: () => <h1>Chi tiết quản lý người chơi</h1>,
}))

describe('Player management routing', () => {
  it('provides an obvious Home navigation path to /players', async () => {
    const user = userEvent.setup()
    render(<App />, { wrapper: MemoryRouter })
    const link = screen.getByRole('link', { name: 'Quản lý người chơi' })
    expect(link).toHaveAttribute('href', '/players')
    await user.click(link)
    expect(screen.getByRole('heading', { name: 'Danh sách quản lý người chơi' }))
      .toBeInTheDocument()
  })

  it('registers the Player detail route without changing Session routes', () => {
    render(
      <MemoryRouter initialEntries={['/players/player-1']}>
        <App />
      </MemoryRouter>,
    )
    expect(screen.getByRole('heading', { name: 'Chi tiết quản lý người chơi' }))
      .toBeInTheDocument()
  })
})
