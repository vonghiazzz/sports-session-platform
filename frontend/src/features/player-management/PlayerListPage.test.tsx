import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PlayerResponse } from '../../api/contracts'
import { getPlayers } from '../../api/playerApi'
import { PlayerListPage } from './PlayerListPage'

vi.mock('../../api/playerApi', () => ({
  getPlayers: vi.fn(),
  getPlayer: vi.fn(),
  updatePlayerSkillLevel: vi.fn(),
}))

const initialPlayer: PlayerResponse = {
  id: 'player-1',
  playerCode: 'P000001',
  displayName: 'Nguyễn An',
  sportProfiles: [{
    id: 'profile-1',
    sport: 'BADMINTON',
    skillLevel: 'INTERMEDIATE_PLUS',
    rating: {
      ratingValue: 31,
      uncertainty: 8.333333333,
      ratedMatches: 0,
      ratingBasis: 'INITIAL_PRIOR',
      ratingAlgorithmVersion: null,
    },
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  }],
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

const persistedPlayer: PlayerResponse = {
  ...initialPlayer,
  id: 'player-2',
  playerCode: 'P000002',
  displayName: 'Trần Bình',
  sportProfiles: [{
    ...initialPlayer.sportProfiles[0],
    id: 'profile-2',
    skillLevel: 'INTERMEDIATE',
    rating: {
      ratingValue: 28.765432109,
      uncertainty: 4.8,
      ratedMatches: 14,
      ratingBasis: 'PERSISTED',
      ratingAlgorithmVersion: 'weng-lin-pl-v1',
    },
  }],
}

function deferred<T>() {
  let resolvePromise!: (value: T) => void
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve
  })
  return { promise, resolve: resolvePromise }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }
  return { user: userEvent.setup(), ...render(<PlayerListPage />, { wrapper: Wrapper }) }
}

describe('PlayerListPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getPlayers).mockResolvedValue([initialPlayer, persistedPlayer])
  })

  it('shows a dedicated loading state', () => {
    vi.mocked(getPlayers).mockReturnValue(deferred<readonly PlayerResponse[]>().promise)
    renderPage()
    expect(screen.getByText('Đang tải người chơi...')).toBeInTheDocument()
  })

  it('renders Player codes, names, Vietnamese Skill labels, Rating, and rated matches', async () => {
    renderPage()
    expect(await screen.findByText('P000001')).toBeInTheDocument()
    expect(screen.getByText('P000002')).toBeInTheDocument()
    expect(screen.getByText('Nguyễn An')).toBeInTheDocument()
    expect(screen.getByText('Trình: TB+')).toBeInTheDocument()
    expect(screen.getByText('31,0')).toBeInTheDocument()
    expect(screen.getByText('Chưa có trận được tính điểm xếp hạng')).toBeInTheDocument()
    expect(screen.getByText('28,77')).toBeInTheDocument()
    expect(screen.getByText('14 trận đã tính điểm xếp hạng')).toBeInTheDocument()
  })

  it('presents INITIAL_PRIOR and PERSISTED as distinct Host concepts', async () => {
    renderPage()
    expect(
      await screen.findByText(
        'Điểm khởi tạo theo trình độ người tổ chức đánh giá',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Điểm xếp hạng đã học từ kết quả thi đấu'),
    ).toBeInTheDocument()
  })

  it('submits normalized search to the backend only on explicit action', async () => {
    const { user } = renderPage()
    await screen.findByText('Nguyễn An')
    await user.type(screen.getByLabelText('Tên người chơi'), '  Nguyễn An  ')
    expect(getPlayers).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }))
    expect(getPlayers).toHaveBeenLastCalledWith('Nguyễn An', expect.any(AbortSignal))
  })

  it('submits a global Player code through the existing search flow', async () => {
    const { user } = renderPage()
    await screen.findByText('P000001')
    await user.type(screen.getByLabelText('Tên người chơi'), '  p000001  ')
    await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }))

    expect(getPlayers).toHaveBeenLastCalledWith(
      'p000001',
      expect.any(AbortSignal),
    )
  })

  it('distinguishes duplicate names while detail navigation remains UUID-based', async () => {
    vi.mocked(getPlayers).mockResolvedValue([
      initialPlayer,
      { ...persistedPlayer, displayName: initialPlayer.displayName },
    ])
    renderPage()

    expect(await screen.findAllByText('Nguyễn An')).toHaveLength(2)
    expect(screen.getByText('P000001')).toBeInTheDocument()
    expect(screen.getByText('P000002')).toBeInTheDocument()
    const links = screen.getAllByRole('link', { name: 'Xem chi tiết' })
    expect(links[0]).toHaveAttribute('href', '/players/player-1')
    expect(links[1]).toHaveAttribute('href', '/players/player-2')
  })

  it('shows the base empty state', async () => {
    vi.mocked(getPlayers).mockResolvedValue([])
    renderPage()
    expect(await screen.findByText('Chưa có người chơi.')).toBeInTheDocument()
  })

  it('shows a distinct zero-search-result state', async () => {
    vi.mocked(getPlayers).mockResolvedValue([])
    const { user } = renderPage()
    await screen.findByText('Chưa có người chơi.')
    await user.type(screen.getByLabelText('Tên người chơi'), 'Không có')
    await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }))
    expect(await screen.findByText('Không tìm thấy người chơi phù hợp.')).toBeInTheDocument()
  })

  it('keeps a scoped error with a retry action', async () => {
    vi.mocked(getPlayers)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce([initialPlayer])
    const { user } = renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải')
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Nguyễn An')).toBeInTheDocument()
  })

  it('links each result to its Player detail route', async () => {
    renderPage()
    const links = await screen.findAllByRole('link', { name: 'Xem chi tiết' })
    expect(links).toHaveLength(2)
    expect(links[0]).toHaveAttribute('href', '/players/player-1')
  })
})
