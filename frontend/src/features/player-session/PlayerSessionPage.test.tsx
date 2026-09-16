import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { MatchPlanResponse } from '../../api/contracts'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import type {
  LiveSessionData,
  LiveSessionDataState,
} from '../live-session/useLiveSessionData'
import { PlayerSessionScreen } from './PlayerSessionPage'

const refresh = vi.fn(async () => undefined)

function readyState(data: LiveSessionData): LiveSessionDataState {
  return {
    status: 'ready',
    data,
    refresh,
    isRefreshing: false,
  }
}

function queuedPlan(): MatchPlanResponse {
  return {
    id: 'plan-1',
    sessionId: 'session-1',
    sessionCourtId: 'session-court-2',
    source: 'RECOMMENDATION',
    status: 'QUEUED',
    queuePosition: 1,
    startedMatchId: null,
    participants: [
      {
        id: 'plan-participant-1',
        sessionParticipantId: 'participant-1',
        teamSide: 'A',
        teamSlot: 1,
      },
      {
        id: 'plan-participant-2',
        sessionParticipantId: 'participant-2',
        teamSide: 'A',
        teamSlot: 2,
      },
      {
        id: 'plan-participant-3',
        sessionParticipantId: 'participant-3',
        teamSide: 'B',
        teamSlot: 1,
      },
      {
        id: 'plan-participant-4',
        sessionParticipantId: 'participant-4',
        teamSide: 'B',
        teamSlot: 2,
      },
    ],
    createdAt: '2026-09-02T09:55:00Z',
    startedAt: null,
    cancelledAt: null,
    updatedAt: '2026-09-02T09:55:00Z',
    version: 0,
  }
}

describe('PlayerSessionScreen', () => {
  beforeEach(() => {
    refresh.mockClear()
  })

  it('renders Participant code, name, and REGISTERED state', () => {
    render(
      <PlayerSessionScreen
        state={readyState(createLiveSessionInput())}
        sessionParticipantId="participant-3"
      />,
    )

    expect(screen.getByRole('heading', { name: '#3 Chi Le' })).toBeVisible()
    expect(screen.getByText('Đã đăng ký')).toHaveAttribute(
      'data-player-state',
      'REGISTERED',
    )
  })

  it('renders WAITING without inventing match details', () => {
    render(
      <PlayerSessionScreen
        state={readyState(createLiveSessionInput())}
        sessionParticipantId="participant-1"
      />,
    )

    expect(screen.getByText('Đang chờ')).toHaveAttribute(
      'data-player-state',
      'WAITING',
    )
    expect(screen.queryByRole('heading', { name: /Trận/ })).not.toBeInTheDocument()
  })

  it('manually refreshes authoritative data without leaving the Player view', async () => {
    const user = userEvent.setup()
    const input = createLiveSessionInput()
    const rendered = render(
      <PlayerSessionScreen
        state={readyState(input)}
        sessionParticipantId="participant-1"
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Làm mới' }))
    expect(refresh).toHaveBeenCalledOnce()
    expect(screen.getByRole('heading', { name: '#1 An Nguyen' })).toBeVisible()

    rendered.rerender(
      <PlayerSessionScreen
        state={readyState({
          ...input,
          participants: input.participants.map((participant) =>
            participant.id === 'participant-1'
              ? {
                  ...participant,
                  status: 'PAUSED',
                  waitingSince: null,
                  pausedAt: '2026-09-02T10:01:00Z',
                }
              : participant,
          ),
        })}
        sessionParticipantId="participant-1"
      />,
    )
    expect(screen.getByText('Tạm nghỉ')).toHaveAttribute(
      'data-player-state',
      'PAUSED',
    )
  })

  it('shows an explicit disabled pending state for manual Refresh', () => {
    render(
      <PlayerSessionScreen
        state={{
          ...readyState(createLiveSessionInput()),
          isRefreshing: true,
        }}
        sessionParticipantId="participant-1"
      />,
    )

    expect(
      screen.getByRole('button', { name: 'Đang làm mới…' }),
    ).toBeDisabled()
  })

  it('keeps the last successful Player state after a refresh failure', () => {
    render(
      <PlayerSessionScreen
        state={{
          ...readyState(createLiveSessionInput()),
          hasBackgroundError: true,
        }}
        sessionParticipantId="participant-1"
      />,
    )

    expect(screen.getByRole('heading', { name: '#1 An Nguyen' })).toBeVisible()
    expect(screen.getByRole('status')).toHaveTextContent(
      'Đang hiển thị dữ liệu gần nhất',
    )
    expect(screen.getByRole('button', { name: 'Làm mới' })).toBeEnabled()
  })

  it('renders QUEUED Court, teammate, and two opponents with codes', () => {
    const input = createLiveSessionInput()
    render(
      <PlayerSessionScreen
        state={readyState({ ...input, matchPlans: [queuedPlan()] })}
        sessionParticipantId="participant-1"
      />,
    )

    expect(screen.getByText('Đang trong hàng đợi')).toHaveAttribute(
      'data-player-state',
      'QUEUED',
    )
    const activity = screen.getByRole('heading', {
      name: 'Trận trong hàng đợi',
    }).closest('section')
    expect(activity).not.toBeNull()
    const details = within(activity!)
    expect(details.getByText('Court Two')).toBeVisible()
    expect(details.getByText('#2 Bao Tran')).toBeVisible()
    expect(details.getByText('#3 Chi Le')).toBeVisible()
    expect(details.getByText('#4 Dung Pham')).toBeVisible()
  })

  it('renders PLAYING Court and UUID-resolved teams', () => {
    render(
      <PlayerSessionScreen
        state={readyState(createLiveSessionInput())}
        sessionParticipantId="participant-5"
      />,
    )

    expect(screen.getByText('Đang chơi')).toHaveAttribute(
      'data-player-state',
      'PLAYING',
    )
    expect(screen.getByText('Court One')).toBeVisible()
    expect(screen.getByText('#6 Hanh Bui')).toBeVisible()
    expect(screen.getByText('#7 Khanh Do')).toBeVisible()
    expect(screen.getByText('#8 Linh Ho')).toBeVisible()
  })

  it.each([
    ['PAUSED', 'Tạm nghỉ'],
    ['LEFT', 'Đã rời'],
  ] as const)('renders the authoritative %s state', (status, label) => {
    const input = createLiveSessionInput()
    const participants = input.participants.map((participant) =>
      participant.id === 'participant-1'
        ? { ...participant, status }
        : participant,
    )

    render(
      <PlayerSessionScreen
        state={readyState({ ...input, participants })}
        sessionParticipantId="participant-1"
      />,
    )

    expect(screen.getByText(label)).toHaveAttribute('data-player-state', status)
  })

  it('shows safe fallback messaging for incomplete related data', () => {
    const input = createLiveSessionInput()
    const plan = queuedPlan()
    render(
      <PlayerSessionScreen
        state={readyState({
          ...input,
          matchPlans: [
            {
              ...plan,
              participants: plan.participants.slice(0, 1),
            },
          ],
        })}
        sessionParticipantId="participant-1"
      />,
    )

    expect(screen.getAllByText('Chưa có dữ liệu')).toHaveLength(2)
    expect(screen.getByRole('status')).toHaveTextContent(
      'Một phần thông tin trận chưa đầy đủ',
    )
  })

  it('distinguishes an unknown Session from an unknown Participant', () => {
    const { rerender } = render(
      <PlayerSessionScreen
        state={{
          status: 'not-found',
          refresh,
          isRefreshing: false,
        }}
        sessionParticipantId="participant-1"
      />,
    )
    expect(screen.getByRole('heading', { name: 'Không tìm thấy phiên' })).toBeVisible()

    rerender(
      <PlayerSessionScreen
        state={readyState(createLiveSessionInput())}
        sessionParticipantId="missing-participant"
      />,
    )
    expect(
      screen.getByRole('heading', { name: 'Không tìm thấy người tham gia' }),
    ).toBeVisible()
  })

  it.each(['COMPLETED', 'CANCELLED'] as const)(
    'keeps a terminal %s Session view read-only',
    (status) => {
      const input = createLiveSessionInput()
      render(
        <PlayerSessionScreen
          state={readyState({
            ...input,
            session: { ...input.session, status },
          })}
          sessionParticipantId="participant-3"
        />,
      )

      expect(screen.getByRole('heading', { name: '#3 Chi Le' })).toBeVisible()
      expect(screen.getByText(status === 'COMPLETED' ? 'Đã kết thúc' : 'Đã hủy'))
        .toBeVisible()
      expect(screen.getByRole('button', { name: 'Làm mới' })).toBeEnabled()
    },
  )
})
