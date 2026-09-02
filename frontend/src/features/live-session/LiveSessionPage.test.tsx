import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { LiveSessionScreen } from './LiveSessionPage'
import type { LiveSessionDataState } from './useLiveSessionData'

const refresh = vi.fn(async () => undefined)
const now = new Date('2026-09-02T10:00:00Z')

function state(
  status: 'loading' | 'not-found' | 'error',
): LiveSessionDataState {
  return {
    status,
    refresh,
    isRefreshing: false,
  }
}

describe('LiveSessionScreen', () => {
  beforeEach(() => {
    refresh.mockClear()
  })

  it('renders a distinct initial loading state', () => {
    render(<LiveSessionScreen state={state('loading')} now={now} />)

    expect(screen.getByRole('heading', { name: 'Loading Session…' })).toBeVisible()
    expect(screen.getByText('Gathering Courts, Players, and Matches.')).toBeVisible()
  })

  it('renders a clear Session not found state', () => {
    render(<LiveSessionScreen state={state('not-found')} now={now} />)

    expect(screen.getByRole('heading', { name: 'Session not found' })).toBeVisible()
  })

  it('renders an essential-read error separately from empty data', () => {
    render(<LiveSessionScreen state={state('error')} now={now} />)

    expect(
      screen.getByRole('heading', {
        name: 'Unable to load live Session data.',
      }),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled()
  })

  it('renders realistic multi-Court and multi-Participant data read-only', () => {
    const { now: fixtureNow, ...data } = createLiveSessionInput()
    const readyState: LiveSessionDataState = {
      status: 'ready',
      data,
      refresh,
      isRefreshing: false,
    }

    render(<LiveSessionScreen state={readyState} now={fixtureNow} />)

    expect(
      screen.getByRole('heading', { name: 'Wednesday Badminton' }),
    ).toBeVisible()
    expect(screen.getByText('Riverside Sports Hall · District 2')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Court One' })).toBeVisible()
    expect(screen.getAllByRole('heading', { name: 'Court Two' })).toHaveLength(2)
    expect(screen.getByRole('heading', { name: 'Court Three' })).toBeVisible()
    expect(screen.getAllByText('An Nguyen')).toHaveLength(2)
    expect(screen.getByText('30 min')).toBeVisible()
    expect(screen.getByText('Created — not started')).toBeVisible()
    expect(screen.queryByText(/reserved/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeEnabled()
  })

  it('uses the single manual Refresh control for read refresh', async () => {
    const user = userEvent.setup()
    const { now: fixtureNow, ...data } = createLiveSessionInput()
    const readyState: LiveSessionDataState = {
      status: 'ready',
      data,
      refresh,
      isRefreshing: false,
    }
    render(<LiveSessionScreen state={readyState} now={fixtureNow} />)

    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(refresh).toHaveBeenCalledOnce()
  })
})
