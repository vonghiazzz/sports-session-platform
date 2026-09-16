import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  PlayerResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  checkInParticipant,
  getSessionParticipantPersonalAccess,
} from '../../api/liveSessionApi'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { HostCheckInScreen } from './HostCheckInPage'
import type { HostCheckInDataState } from './useHostCheckInData'

vi.mock('../../api/liveSessionApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/liveSessionApi')>()),
  checkInParticipant: vi.fn(),
  getSessionParticipantPersonalAccess: vi.fn(),
}))

vi.mock('qrcode.react', () => ({
  QRCodeSVG: ({ value, title }: { value: string; title: string }) => (
    <svg data-testid="personal-qr-code" data-qr-value={value} role="img">
      <title>{title}</title>
    </svg>
  ),
}))

const checkInParticipantMock = vi.mocked(checkInParticipant)
const getPersonalAccessMock = vi.mocked(getSessionParticipantPersonalAccess)
const queryClients: QueryClient[] = []
const refresh = vi.fn(async () => undefined)

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function readyState(
  participants?: readonly SessionParticipantResponse[],
  players?: readonly PlayerResponse[],
  sessionStatus?: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED',
): Extract<HostCheckInDataState, { readonly status: 'ready' }> {
  const input = createLiveSessionInput()
  return {
    status: 'ready',
    data: {
      session: {
        ...input.session,
        status: sessionStatus ?? input.session.status,
      },
      participants: participants ?? input.participants,
      players: players ?? input.players,
    },
    refresh,
    isRefreshing: false,
  }
}

function renderDesk(state = readyState()) {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  })
  queryClients.push(queryClient)
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <HostCheckInScreen sessionId="session-1" state={state} />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

function participantRow(label: string) {
  return screen.getByRole('listitem', { name: label })
}

beforeEach(() => {
  vi.resetAllMocks()
  refresh.mockResolvedValue(undefined)
})

afterEach(() => {
  queryClients.forEach((queryClient) => queryClient.clear())
  queryClients.length = 0
})

describe('HostCheckInScreen', () => {
  it('renders a participant-code ordered list, accurate summary, and Control Room return path', () => {
    const input = createLiveSessionInput()
    const participants = [
      { ...input.participants[2], participantCode: 12 },
      { ...input.participants[0], participantCode: 3 },
      { ...input.participants[1], participantCode: 8 },
    ]
    renderDesk(readyState(participants))

    expect(screen.getByRole('heading', { name: 'Bàn điểm danh' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Quay lại phòng điều hành' })).toHaveAttribute(
      'href',
      '/sessions/session-1',
    )
    expect(
      screen.getAllByRole('listitem').map((row) => row.getAttribute('aria-label')),
    ).toEqual(['#3 An Nguyen', '#8 Bao Tran', '#12 Chi Le'])
    const summary = screen.getByLabelText('Tổng quan điểm danh')
    expect(within(summary).getByText('Chưa điểm danh').nextSibling).toHaveTextContent(
      '1',
    )
    expect(within(summary).getByText('Đã từng điểm danh').nextSibling).toHaveTextContent(
      '2',
    )
    expect(within(summary).getByText('Tổng người chơi').nextSibling).toHaveTextContent(
      '3',
    )
  })

  it('searches exact codes with or without hash and duplicate names case-insensitively', async () => {
    const user = userEvent.setup()
    const input = createLiveSessionInput()
    const players = input.players.map((player) =>
      player.id === 'player-1' || player.id === 'player-2'
        ? { ...player, displayName: 'Nguyễn An' }
        : player,
    )
    renderDesk(readyState(input.participants, players))
    const search = screen.getByRole('searchbox', {
      name: 'Tìm theo mã hoặc tên',
    })

    await user.type(search, 'NGUYEN an')
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(participantRow('#1 Nguyễn An')).toBeVisible()
    expect(participantRow('#2 Nguyễn An')).toBeVisible()

    await user.clear(search)
    await user.type(search, '#1')
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
    expect(participantRow('#1 Nguyễn An')).toBeVisible()

    await user.clear(search)
    await user.type(search, ' 2 ')
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
    expect(participantRow('#2 Nguyễn An')).toBeVisible()
  })

  it('checks in by SessionParticipant UUID and preserves GET state until authoritative data changes', async () => {
    const user = userEvent.setup()
    const input = createLiveSessionInput()
    const registered = input.participants[2]
    const request = deferred<SessionParticipantResponse>()
    checkInParticipantMock.mockReturnValue(request.promise)
    renderDesk(readyState())
    const row = participantRow('#3 Chi Le')

    await user.click(within(row).getByRole('button', { name: 'Điểm danh' }))

    expect(checkInParticipantMock).toHaveBeenCalledOnce()
    expect(checkInParticipantMock).toHaveBeenCalledWith('session-1', registered.id)
    expect(checkInParticipantMock).not.toHaveBeenCalledWith('session-1', 3)
    expect(within(row).getByText('Đã đăng ký')).toBeVisible()
    expect(
      within(row).getByRole('button', { name: 'Đang điểm danh…' }),
    ).toBeDisabled()

    request.resolve({
      ...registered,
      status: 'WAITING',
      checkedInAt: '2026-09-02T10:00:00Z',
      waitingSince: '2026-09-02T10:00:00Z',
    })
    await waitFor(() =>
      expect(within(row).getByRole('button', { name: 'Điểm danh' })).toBeEnabled(),
    )
    expect(within(row).getByText('Đã đăng ký')).toBeVisible()
  })

  it('scopes pending state to one duplicate-name row', async () => {
    const user = userEvent.setup()
    const input = createLiveSessionInput()
    const registeredA = input.participants[2]
    const registeredB = {
      ...registeredA,
      id: 'participant-second',
      playerId: 'player-4',
      participantCode: 4,
    }
    const players = input.players.map((player) =>
      player.id === 'player-3' || player.id === 'player-4'
        ? { ...player, displayName: 'Nguyễn An' }
        : player,
    )
    const request = deferred<SessionParticipantResponse>()
    checkInParticipantMock.mockReturnValue(request.promise)
    renderDesk(readyState([registeredA, registeredB], players))

    await user.click(
      within(participantRow('#3 Nguyễn An')).getByRole('button', {
        name: 'Điểm danh',
      }),
    )

    expect(
      within(participantRow('#3 Nguyễn An')).getByRole('button', {
        name: 'Đang điểm danh…',
      }),
    ).toBeDisabled()
    expect(
      within(participantRow('#4 Nguyễn An')).getByRole('button', {
        name: 'Điểm danh',
      }),
    ).toBeEnabled()
    request.resolve({ ...registeredA, status: 'WAITING' })
  })

  it('shows a safe error without false WAITING and permits an explicit retry', async () => {
    const user = userEvent.setup()
    const input = createLiveSessionInput()
    const registered = input.participants[2]
    checkInParticipantMock
      .mockRejectedValueOnce(new HttpError(409, 'conflict details'))
      .mockResolvedValueOnce({ ...registered, status: 'WAITING' })
    renderDesk()
    const row = participantRow('#3 Chi Le')

    await user.click(within(row).getByRole('button', { name: 'Điểm danh' }))

    expect(await within(row).findByRole('alert')).toHaveTextContent(
      'Trạng thái trực tiếp đã thay đổi',
    )
    expect(within(row).getByText('Đã đăng ký')).toBeVisible()
    expect(checkInParticipantMock).toHaveBeenCalledTimes(1)

    await user.click(within(row).getByRole('button', { name: 'Điểm danh' }))
    await waitFor(() => expect(checkInParticipantMock).toHaveBeenCalledTimes(2))
  })

  it.each(['WAITING', 'PLAYING', 'PAUSED', 'LEFT'] as const)(
    'does not offer Check-In for a %s participant',
    (status) => {
      const input = createLiveSessionInput()
      const participant = {
        ...input.participants[2],
        status,
        checkedInAt: status === 'LEFT' ? null : input.participants[0].checkedInAt,
      }
      renderDesk(readyState([participant]))

      expect(screen.queryByRole('button', { name: 'Điểm danh' })).not.toBeInTheDocument()
      expect(
        screen.getByText(
          {
            WAITING: 'Đang chờ',
            PLAYING: 'Đang chơi',
            PAUSED: 'Tạm nghỉ',
            LEFT: 'Đã rời',
          }[status],
        ),
      ).toBeVisible()
    },
  )

  it.each(['PLANNED', 'COMPLETED', 'CANCELLED'] as const)(
    'renders a REGISTERED participant read-only for a %s Session',
    (sessionStatus) => {
      const input = createLiveSessionInput()
      renderDesk(readyState([input.participants[2]], undefined, sessionStatus))

      expect(screen.queryByRole('button', { name: 'Điểm danh' })).not.toBeInTheDocument()
      expect(
        screen.getByText('Phiên phải đang diễn ra để điểm danh.'),
      ).toBeVisible()
    },
  )

  it('reuses the read-only personal QR action without triggering Check-In', async () => {
    const user = userEvent.setup()
    getPersonalAccessMock.mockResolvedValue({
      sessionId: 'session-1',
      sessionParticipantId: 'participant-3',
      personalAccessToken: 'opaque-token',
    })
    renderDesk()

    await user.click(
      within(participantRow('#3 Chi Le')).getByRole('button', {
        name: 'QR người chơi cho #3 Chi Le',
      }),
    )

    expect(getPersonalAccessMock).toHaveBeenCalledWith('session-1', 'participant-3')
    expect(await screen.findByRole('dialog')).toBeVisible()
    expect(screen.getByTestId('personal-qr-code')).toHaveAttribute(
      'data-qr-value',
      expect.stringContaining('/player-session/opaque-token'),
    )
    expect(checkInParticipantMock).not.toHaveBeenCalled()
  })
})
