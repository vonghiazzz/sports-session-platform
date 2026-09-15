import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { SessionResponse, SessionStatus } from '../../api/contracts'
import { getSessions } from '../../api/sessionDiscoveryApi'
import { SessionDiscoveryList } from './SessionDiscoveryList'

vi.mock('../../api/sessionDiscoveryApi', () => ({
  getSessions: vi.fn(),
}))

const baseSession: SessionResponse = {
  id: '10000000-0000-0000-0000-000000000001',
  venueId: '20000000-0000-0000-0000-000000000001',
  title: 'Đánh cầu tối thứ 2',
  sport: 'BADMINTON',
  matchFormat: 'DOUBLES',
  plannedStartAt: '2026-09-15T11:00:00Z',
  plannedEndAt: '2026-09-15T13:00:00Z',
  status: 'IN_PROGRESS',
  startedAt: '2026-09-15T11:00:00Z',
  completedAt: null,
  cancelledAt: null,
  version: 1,
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-15T11:00:00Z',
}

function session(
  id: string,
  status: SessionStatus,
  title = `${status} Session`,
): SessionResponse {
  return {
    ...baseSession,
    id,
    title,
    status,
    startedAt: status === 'PLANNED' ? null : baseSession.startedAt,
    completedAt: status === 'COMPLETED' ? '2026-09-15T13:00:00Z' : null,
    cancelledAt: status === 'CANCELLED' ? '2026-09-15T12:00:00Z' : null,
  }
}

function deferred<T>() {
  let resolvePromise!: (value: T) => void
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve
  })
  return { promise, resolve: resolvePromise }
}

function renderList() {
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
  return { user: userEvent.setup(), ...render(<SessionDiscoveryList />, { wrapper: Wrapper }) }
}

describe('SessionDiscoveryList', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getSessions).mockResolvedValue([baseSession])
  })

  it('shows a dedicated loading state', () => {
    vi.mocked(getSessions).mockReturnValue(
      deferred<readonly SessionResponse[]>().promise,
    )
    renderList()
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải danh sách phiên…')
  })

  it('shows an empty state with the existing create flow', async () => {
    vi.mocked(getSessions).mockResolvedValue([])
    renderList()
    expect(await screen.findByText('Chưa có phiên nào.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Tạo phiên mới' })).toHaveAttribute(
      'href',
      '/sessions/new',
    )
  })

  it('keeps a safe error local to discovery and supports retry', async () => {
    vi.mocked(getSessions)
      .mockRejectedValueOnce(new Error('database unavailable'))
      .mockResolvedValueOnce([baseSession])
    const { user } = renderList()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không thể tải danh sách phiên.',
    )
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText(baseSession.title)).toBeVisible()
  })

  it('renders all statuses with Vietnamese labels and status-specific actions', async () => {
    vi.mocked(getSessions).mockResolvedValue([
      session('session-in-progress', 'IN_PROGRESS'),
      session('session-planned', 'PLANNED'),
      session('session-completed', 'COMPLETED'),
      session('session-cancelled', 'CANCELLED'),
    ])
    renderList()

    expect(await screen.findByText('Đang diễn ra')).toBeVisible()
    expect(screen.getByText('Đã lên kế hoạch')).toBeVisible()
    expect(screen.getByText('Đã kết thúc')).toBeVisible()
    expect(screen.getByText('Đã hủy')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Mở Control Room' })).toHaveAttribute(
      'href',
      '/sessions/session-in-progress',
    )
    expect(screen.getByRole('link', { name: 'Mở phiên' })).toHaveAttribute(
      'href',
      '/sessions/session-planned',
    )
    expect(screen.getAllByRole('link', { name: 'Xem phiên' })).toHaveLength(2)
  })

  it('uses Vietnam time and never renders the raw UTC timestamp', async () => {
    renderList()
    expect(await screen.findByText('18:00 15/09/2026')).toBeVisible()
    expect(screen.queryByText(baseSession.plannedStartAt)).not.toBeInTheDocument()
  })

  it('preserves backend ordering instead of sorting the response again', async () => {
    vi.mocked(getSessions).mockResolvedValue([
      session('session-b', 'COMPLETED', 'Được backend xếp trước'),
      session('session-a', 'IN_PROGRESS', 'Được backend xếp sau'),
    ])
    renderList()

    const titles = await screen.findAllByRole('heading', { level: 3 })
    expect(titles.map((title) => title.textContent)).toEqual([
      'Được backend xếp trước',
      'Được backend xếp sau',
    ])
  })

  it('uses UUID links to distinguish duplicate Session titles', async () => {
    vi.mocked(getSessions).mockResolvedValue([
      session('session-uuid-1', 'PLANNED', 'Tên phiên trùng'),
      session('session-uuid-2', 'PLANNED', 'Tên phiên trùng'),
    ])
    renderList()

    const cards = await screen.findAllByRole('article')
    expect(cards).toHaveLength(2)
    expect(within(cards[0]).getByRole('link', { name: 'Mở phiên' })).toHaveAttribute(
      'href',
      '/sessions/session-uuid-1',
    )
    expect(within(cards[1]).getByRole('link', { name: 'Mở phiên' })).toHaveAttribute(
      'href',
      '/sessions/session-uuid-2',
    )
  })
})
