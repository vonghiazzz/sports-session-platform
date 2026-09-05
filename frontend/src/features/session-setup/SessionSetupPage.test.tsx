import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PropsWithChildren } from 'react'
import type { CourtResponse, PlayerResponse, VenueResponse } from '../../api/contracts'
import { SessionSetupPage } from './SessionSetupPage'
import { useSessionSetupData } from './useSessionSetupData'
import { useSessionSetupExecution } from './useSessionSetupExecution'

vi.mock('./useSessionSetupData')
vi.mock('./useSessionSetupExecution')

const createVenueMock = vi.fn()
const createCourtMock = vi.fn()
const createPlayerMock = vi.fn()
const executeMock = vi.fn()

const venues: readonly VenueResponse[] = [
  {
    id: 'venue-1',
    name: 'Nhà thi đấu A',
    locationText: 'Quận 1',
    active: true,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  },
  {
    id: 'venue-2',
    name: 'Nhà thi đấu B',
    locationText: null,
    active: true,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  },
]

const courts: readonly CourtResponse[] = [
  {
    id: 'court-1',
    venueId: 'venue-1',
    name: 'Sân 1',
    sport: 'BADMINTON',
    active: true,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  },
  {
    id: 'court-2',
    venueId: 'venue-1',
    name: 'Sân 2',
    sport: 'BADMINTON',
    active: true,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  },
]

const players: readonly PlayerResponse[] = [
  {
    id: 'player-1',
    displayName: 'Nguyễn An',
    sportProfiles: [
      {
        id: 'profile-1',
        sport: 'BADMINTON',
        skillLevel: 'INTERMEDIATE_PLUS',
        createdAt: '2026-09-01T00:00:00Z',
        updatedAt: '2026-09-01T00:00:00Z',
      },
    ],
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  },
  {
    id: 'player-2',
    displayName: 'Trần Bình',
    sportProfiles: [
      {
        id: 'profile-2',
        sport: 'BADMINTON',
        skillLevel: 'WEAK',
        createdAt: '2026-09-01T00:00:00Z',
        updatedAt: '2026-09-01T00:00:00Z',
      },
    ],
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  },
]

function arrangeHooks() {
  vi.mocked(useSessionSetupData).mockImplementation((venueId) => ({
    venues,
    players,
    courts: venueId === 'venue-1' ? courts : [],
    venuesLoading: false,
    playersLoading: false,
    courtsLoading: false,
    venuesError: false,
    playersError: false,
    courtsError: false,
    createSetupVenue: createVenueMock,
    createSetupCourt: createCourtMock,
    createSetupPlayer: createPlayerMock,
    venueCreationPending: false,
    courtCreationPending: false,
    playerCreationPending: false,
    issue: null,
    clearIssue: vi.fn(),
  }))
  vi.mocked(useSessionSetupExecution).mockReturnValue({
    execute: executeMock,
    isPending: false,
    progressMessage: null,
    sessionId: null,
    errorMessage: null,
    unknownCreateOutcome: false,
  })
}

function renderPage() {
  const queryClient = new QueryClient()
  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }
  return { user: userEvent.setup(), ...render(<SessionSetupPage />, { wrapper: Wrapper }) }
}

describe('SessionSetupPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    arrangeHooks()
  })

  it('loads a selected Venue and clears its Court selections when Venue changes', async () => {
    const { user } = renderPage()

    await user.selectOptions(screen.getByLabelText('Chọn địa điểm'), 'venue-1')
    await user.click(screen.getByRole('checkbox', { name: 'Sân 1' }))
    expect(screen.getByRole('checkbox', { name: 'Sân 1' })).toBeChecked()

    await user.selectOptions(screen.getByLabelText('Chọn địa điểm'), 'venue-2')
    await user.selectOptions(screen.getByLabelText('Chọn địa điểm'), 'venue-1')

    expect(screen.getByRole('checkbox', { name: 'Sân 1' })).not.toBeChecked()
  })

  it('supports compact search, multi-select, and Vietnamese Skill labels', async () => {
    const { user } = renderPage()

    expect(screen.getByText('TB+')).toBeInTheDocument()
    expect(screen.getByText('Yếu')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Tìm người chơi'), 'an')

    expect(screen.getByText('Nguyễn An')).toBeInTheDocument()
    expect(screen.queryByText('Trần Bình')).not.toBeInTheDocument()
    await user.click(screen.getByRole('checkbox', { name: /Nguyễn An/ }))
    expect(screen.getByText('Đã chọn 1 người chơi.')).toBeInTheDocument()
  })

  it('creates a Venue and a Court with fixed active BADMINTON values', async () => {
    createVenueMock.mockResolvedValue({ ...venues[0], id: 'venue-new' })
    createCourtMock.mockResolvedValue({ ...courts[0], id: 'court-new' })
    const { user } = renderPage()

    await user.click(screen.getByRole('button', { name: '+ Tạo địa điểm mới' }))
    await user.type(screen.getByLabelText('Tên địa điểm'), 'Nhà thi đấu mới')
    await user.type(screen.getByLabelText('Địa chỉ hoặc mô tả (không bắt buộc)'), 'Quận 7')
    await user.click(screen.getByRole('button', { name: 'Tạo địa điểm' }))

    expect(createVenueMock).toHaveBeenCalledWith({
      name: 'Nhà thi đấu mới',
      locationText: 'Quận 7',
      active: true,
    })

    await user.selectOptions(screen.getByLabelText('Chọn địa điểm'), 'venue-1')
    await user.click(screen.getByRole('button', { name: '+ Thêm sân' }))
    await user.type(screen.getByLabelText('Tên sân'), 'Sân mới')
    await user.click(screen.getByRole('button', { name: 'Thêm sân' }))

    expect(createCourtMock).toHaveBeenCalledWith('venue-1', {
      name: 'Sân mới',
      sport: 'BADMINTON',
      active: true,
    })
  })

  it('creates a missing Player with the backend Skill enum and selects its ID', async () => {
    createPlayerMock.mockResolvedValue({
      ...players[0],
      id: 'player-new',
      displayName: 'Lê Chi',
    })
    const { user } = renderPage()

    await user.type(screen.getByLabelText('Tìm người chơi'), 'Lê Chi')
    await user.click(screen.getByRole('button', { name: '+ Tạo người chơi mới' }))
    await user.selectOptions(screen.getByLabelText('Trình độ'), 'GOOD')
    await user.click(screen.getByRole('button', { name: 'Tạo người chơi' }))

    expect(createPlayerMock).toHaveBeenCalledWith({
      displayName: 'Lê Chi',
      sport: 'BADMINTON',
      skillLevel: 'GOOD',
    })
    expect(screen.getByText('Đã chọn 1 người chơi.')).toBeInTheDocument()
  })

  it('reviews selections and executes the exact end-to-end setup input', async () => {
    const { user } = renderPage()

    await user.type(screen.getByLabelText('Tiêu đề phiên'), 'Phiên tối')
    await user.type(screen.getByLabelText('Ngày'), '2026-09-05')
    await user.type(screen.getByLabelText('Giờ bắt đầu'), '18:00')
    await user.type(screen.getByLabelText('Giờ kết thúc'), '20:00')
    await user.selectOptions(screen.getByLabelText('Chọn địa điểm'), 'venue-1')
    await user.click(screen.getByRole('checkbox', { name: 'Sân 1' }))
    await user.click(screen.getByRole('checkbox', { name: /Nguyễn An/ }))

    const review = screen.getByRole('heading', { name: 'Xem lại' }).closest('section')
    expect(review).not.toBeNull()
    expect(within(review!).getByText('Nhà thi đấu A')).toBeInTheDocument()
    expect(within(review!).getByText('05/09/2026 · 18:00 – 20:00')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tạo và bắt đầu phiên' }))

    expect(executeMock).toHaveBeenCalledWith({
      session: {
        venueId: 'venue-1',
        title: 'Phiên tối',
        sport: 'BADMINTON',
        matchFormat: 'DOUBLES',
        plannedStartAt: '2026-09-05T11:00:00.000Z',
        plannedEndAt: '2026-09-05T13:00:00.000Z',
      },
      courtIds: ['court-1'],
      playerIds: ['player-1'],
    })
  })
})
