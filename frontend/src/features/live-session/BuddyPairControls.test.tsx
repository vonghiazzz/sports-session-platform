import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { SessionStatus } from '../../api/contracts'
import {
  createBuddyPair,
  removeBuddyPair,
} from '../../api/liveSessionApi'
import { HttpError } from '../../api/http'
import { BuddyPairControls } from './BuddyPairControls'
import type { ParticipantView } from './liveSessionModel'
import { useBuddyPairActions } from './useBuddyPairActions'

vi.mock('../../api/liveSessionApi', () => ({
  createBuddyPair: vi.fn(),
  removeBuddyPair: vi.fn(),
}))

const createBuddyPairMock = vi.mocked(createBuddyPair)
const removeBuddyPairMock = vi.mocked(removeBuddyPair)
const queryClients: QueryClient[] = []

function participant(
  id: string,
  displayName: string,
  buddyPairId: string | null = null,
  participantCode = 1,
): ParticipantView {
  return {
    sessionParticipantId: id,
    participantCode,
    buddyPairId,
    displayName,
    status: 'WAITING',
    skillLevel: 'INTERMEDIATE',
    skillLabel: 'Trung bình',
    waitingSince: '2026-09-14T05:00:00Z',
    waitingDuration: '10 phút',
    dataUnavailable: false,
    plannedMatchCount: 0,
    planningLabel: null,
  }
}

function renderControls(
  participants: readonly ParticipantView[],
  sessionStatus: SessionStatus = 'IN_PROGRESS',
) {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  })
  queryClients.push(queryClient)

  function Controls() {
    const actions = useBuddyPairActions('session-1')
    return (
      <>
        {participants.map((candidate) => (
          <section
            aria-label={`participant-${candidate.sessionParticipantId}`}
            key={candidate.sessionParticipantId}
          >
            <BuddyPairControls
              participant={candidate}
              participants={participants}
              sessionStatus={sessionStatus}
              actions={actions}
            />
          </section>
        ))}
      </>
    )
  }

  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <Controls />
      </QueryClientProvider>,
    ),
    queryClient,
  }
}

function row(participantId: string): HTMLElement {
  return screen.getByRole('region', { name: `participant-${participantId}` })
}

function deferred<T>() {
  let resolvePromise: (value: T | PromiseLike<T>) => void = () => {
    throw new Error('Deferred resolver is unavailable')
  }
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve
  })
  return { promise, resolve: resolvePromise }
}

beforeEach(() => {
  vi.resetAllMocks()
})

afterEach(() => {
  queryClients.forEach((queryClient) => queryClient.clear())
  queryClients.length = 0
})

describe('BuddyPairControls', () => {
  it('offers Buddy creation directly for an unpaired Participant', () => {
    renderControls([
      participant('participant-a', 'An'),
      participant('participant-b', 'Bình'),
    ])

    expect(
      within(row('participant-a')).getByRole('button', {
        name: 'Đánh cùng bạn',
      }),
    ).toBeEnabled()
  })

  it('shows each paired Participant the other current code and display name', () => {
    renderControls([
      participant('participant-a', 'An', 'buddy-1', 12),
      participant('participant-b', 'Bình', 'buddy-1', 27),
    ])

    expect(
      within(row('participant-a')).getByText('Đánh cùng: #27 Bình'),
    ).toBeVisible()
    expect(
      within(row('participant-b')).getByText('Đánh cùng: #12 An'),
    ).toBeVisible()
  })

  it('excludes self and already-paired Participants from second-person selection', async () => {
    const user = userEvent.setup()
    renderControls([
      participant('participant-a', 'An'),
      participant('participant-b', 'Bình'),
      participant('participant-c', 'Chi', 'buddy-1'),
      participant('participant-d', 'Dung', 'buddy-1'),
    ])

    await user.click(
      within(row('participant-a')).getByRole('button', {
        name: 'Đánh cùng bạn',
      }),
    )

    const selector = screen.getByLabelText('Chọn bạn đánh cùng cho An')
    expect(
      within(selector).getByRole('radio', {
        name: 'Chọn Bình (participant-b)',
      }),
    ).toBeEnabled()
    expect(
      within(selector).queryByRole('radio', {
        name: 'Chọn An (participant-a)',
      }),
    ).not.toBeInTheDocument()
    expect(
      within(selector).queryByRole('radio', {
        name: 'Chọn Chi (participant-c)',
      }),
    ).not.toBeInTheDocument()
    expect(
      within(selector).queryByRole('radio', {
        name: 'Chọn Dung (participant-d)',
      }),
    ).not.toBeInTheDocument()
  })

  it('uses Session Participant UUIDs with duplicate names and invalidates authoritative data', async () => {
    const user = userEvent.setup()
    createBuddyPairMock.mockResolvedValue({
      buddyPairId: 'buddy-1',
      sessionId: 'session-1',
      firstSessionParticipantId: 'participant-a',
      secondSessionParticipantId: 'participant-b',
    })
    const { queryClient } = renderControls([
      participant('participant-a', 'Nguyễn An', null, 3),
      participant('participant-b', 'Nguyễn An', null, 8),
      participant('participant-c', 'Nguyễn An', null, 11),
    ])
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    await user.click(
      within(row('participant-a')).getByRole('button', {
        name: 'Đánh cùng bạn',
      }),
    )
    await user.click(
      screen.getByRole('radio', {
        name: 'Chọn Nguyễn An (participant-b)',
      }),
    )
    await user.click(screen.getByRole('button', { name: 'Xác nhận đánh cùng' }))

    await waitFor(() =>
      expect(createBuddyPairMock).toHaveBeenCalledWith(
        'session-1',
        'participant-a',
        'participant-b',
      ),
    )
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: ['sessionParticipants', 'session-1'],
      exact: true,
    })
    expect(
      queryClient
        .getMutationCache()
        .getAll()
        .map((mutation) => mutation.options.retry),
    ).toEqual([false])
    expect(within(row('participant-a')).queryByText(/Đánh cùng:/)).not.toBeInTheDocument()
  })

  it('shows the backend create error without inventing local Buddy state', async () => {
    const user = userEvent.setup()
    createBuddyPairMock.mockRejectedValue(
      new HttpError(409, 'Người chơi đã được ghép bạn.'),
    )
    renderControls([
      participant('participant-a', 'An'),
      participant('participant-b', 'Bình'),
    ])

    await user.click(
      within(row('participant-a')).getByRole('button', {
        name: 'Đánh cùng bạn',
      }),
    )
    await user.click(
      screen.getByRole('radio', { name: 'Chọn Bình (participant-b)' }),
    )
    await user.click(screen.getByRole('button', { name: 'Xác nhận đánh cùng' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Người chơi đã được ghép bạn.',
    )
    expect(createBuddyPairMock).toHaveBeenCalledOnce()
    expect(within(row('participant-a')).queryByText(/Đánh cùng:/)).not.toBeInTheDocument()
    expect(
      within(row('participant-a')).getByRole('button', {
        name: 'Đánh cùng bạn',
      }),
    ).toBeEnabled()
  })

  it('removes by Session and Buddy Pair UUID then invalidates authoritative data', async () => {
    const user = userEvent.setup()
    removeBuddyPairMock.mockResolvedValue(undefined)
    const { queryClient } = renderControls([
      participant('participant-a', 'An', 'buddy-1'),
      participant('participant-b', 'Bình', 'buddy-1'),
    ])
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    await user.click(
      within(row('participant-a')).getByRole('button', {
        name: 'Hủy đánh cùng',
      }),
    )

    await waitFor(() =>
      expect(removeBuddyPairMock).toHaveBeenCalledWith('session-1', 'buddy-1'),
    )
    expect(removeBuddyPairMock).toHaveBeenCalledOnce()
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: ['sessionParticipants', 'session-1'],
      exact: true,
    })
  })

  it('shows the backend remove error and preserves authoritative paired display', async () => {
    const user = userEvent.setup()
    removeBuddyPairMock.mockRejectedValue(
      new HttpError(404, 'Không tìm thấy cặp đánh cùng.'),
    )
    renderControls([
      participant('participant-a', 'An', 'buddy-1'),
      participant('participant-b', 'Bình', 'buddy-1'),
    ])

    await user.click(
      within(row('participant-a')).getByRole('button', {
        name: 'Hủy đánh cùng',
      }),
    )

    expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent(
      'Không tìm thấy cặp đánh cùng.',
    )
    expect(
      within(row('participant-a')).getByText('Đánh cùng: #1 Bình'),
    ).toBeVisible()
    expect(
      within(row('participant-b')).getByText('Đánh cùng: #1 An'),
    ).toBeVisible()
  })

  it.each<SessionStatus>(['COMPLETED', 'CANCELLED'])(
    'hides Buddy mutation controls for a %s Session',
    (sessionStatus) => {
      renderControls(
        [
          participant('participant-a', 'An', 'buddy-1'),
          participant('participant-b', 'Bình', 'buddy-1'),
          participant('participant-c', 'Chi'),
        ],
        sessionStatus,
      )

      expect(screen.getByText('Đánh cùng: #1 Bình')).toBeVisible()
      expect(
        screen.queryByRole('button', { name: 'Hủy đánh cùng' }),
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: 'Đánh cùng bạn' }),
      ).not.toBeInTheDocument()
    },
  )

  it('renders safe generic indicators for malformed one- or three-member Buddy data', () => {
    renderControls([
      participant('participant-a', 'An', 'buddy-one'),
      participant('participant-b', 'Bình', 'buddy-many'),
      participant('participant-c', 'Chi', 'buddy-many'),
      participant('participant-d', 'Dung', 'buddy-many'),
    ])

    expect(screen.getAllByText('Đã ghép bạn')).toHaveLength(4)
  })

  it('blocks duplicate delete for one pair without disabling unrelated Buddy actions', async () => {
    const user = userEvent.setup()
    const pendingDelete = deferred<void>()
    removeBuddyPairMock.mockReturnValue(pendingDelete.promise)
    renderControls([
      participant('participant-a', 'An', 'buddy-1'),
      participant('participant-b', 'Bình', 'buddy-1'),
      participant('participant-c', 'Chi'),
      participant('participant-d', 'Dung'),
    ])

    await user.click(
      within(row('participant-a')).getByRole('button', {
        name: 'Hủy đánh cùng',
      }),
    )

    expect(
      within(row('participant-a')).getByRole('button', { name: 'Đang hủy…' }),
    ).toBeDisabled()
    expect(
      within(row('participant-b')).getByRole('button', { name: 'Đang hủy…' }),
    ).toBeDisabled()
    expect(
      within(row('participant-c')).getByRole('button', {
        name: 'Đánh cùng bạn',
      }),
    ).toBeEnabled()
    await user.click(
      within(row('participant-b')).getByRole('button', { name: 'Đang hủy…' }),
    )
    expect(removeBuddyPairMock).toHaveBeenCalledOnce()

    pendingDelete.resolve(undefined)
    await waitFor(() =>
      expect(
        within(row('participant-a')).getByRole('button', {
          name: 'Hủy đánh cùng',
        }),
      ).toBeEnabled(),
    )
  })
})
