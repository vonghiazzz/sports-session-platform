import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getSessionParticipantPersonalAccess } from '../../api/liveSessionApi'
import { ParticipantPersonalLinkAction } from './ParticipantPersonalLinkAction'

vi.mock('../../api/liveSessionApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/liveSessionApi')>()),
  getSessionParticipantPersonalAccess: vi.fn(),
}))

const getPersonalAccessMock = vi.mocked(getSessionParticipantPersonalAccess)
const clipboardWrite = vi.fn<(value: string) => Promise<void>>()
const queryClients: QueryClient[] = []

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function renderActions() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  queryClients.push(queryClient)
  return render(
    <QueryClientProvider client={queryClient}>
      <div>
        <section aria-label="Người chơi #3 Nguyễn An">
          <strong>#3 Nguyễn An</strong>
          <ParticipantPersonalLinkAction
            sessionId="session-1"
            sessionParticipantId="participant-a"
            participantLabel="#3 Nguyễn An"
          />
        </section>
        <section aria-label="Người chơi #8 Nguyễn An">
          <strong>#8 Nguyễn An</strong>
          <ParticipantPersonalLinkAction
            sessionId="session-1"
            sessionParticipantId="participant-b"
            participantLabel="#8 Nguyễn An"
          />
        </section>
      </div>
    </QueryClientProvider>,
  )
}

function userWithClipboard() {
  const user = userEvent.setup()
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { writeText: clipboardWrite },
  })
  return user
}

beforeEach(() => {
  getPersonalAccessMock.mockReset()
  clipboardWrite.mockReset()
  clipboardWrite.mockResolvedValue(undefined)
})

afterEach(() => {
  queryClients.forEach((queryClient) => queryClient.clear())
  queryClients.length = 0
})

describe('ParticipantPersonalLinkAction', () => {
  it('copies each duplicate-name Participant link by UUID and never renders the token', async () => {
    const user = userWithClipboard()
    getPersonalAccessMock.mockImplementation(async (_sessionId, participantId) => ({
      sessionId: 'session-1',
      sessionParticipantId: participantId,
      personalAccessToken:
        participantId === 'participant-a' ? 'token-a' : 'token-b',
    }))
    renderActions()

    await user.click(
      screen.getByRole('button', {
        name: 'Sao chép link người chơi cho #3 Nguyễn An',
      }),
    )

    expect(getPersonalAccessMock).toHaveBeenLastCalledWith(
      'session-1',
      'participant-a',
    )
    expect(clipboardWrite).toHaveBeenLastCalledWith(
      new URL('/player-session/token-a', window.location.origin).toString(),
    )
    expect(await screen.findByText('Đã sao chép link')).toBeVisible()
    expect(screen.queryByText('token-a')).not.toBeInTheDocument()

    await user.click(
      screen.getByRole('button', {
        name: 'Sao chép link người chơi cho #8 Nguyễn An',
      }),
    )

    expect(getPersonalAccessMock).toHaveBeenLastCalledWith(
      'session-1',
      'participant-b',
    )
    expect(clipboardWrite).toHaveBeenLastCalledWith(
      new URL('/player-session/token-b', window.location.origin).toString(),
    )
    expect(screen.queryByText('token-b')).not.toBeInTheDocument()
  })

  it('scopes pending state to the clicked Session Participant', async () => {
    const user = userWithClipboard()
    const request = deferred<{
      sessionId: string
      sessionParticipantId: string
      personalAccessToken: string
    }>()
    getPersonalAccessMock.mockReturnValueOnce(request.promise)
    renderActions()

    const firstButton = screen.getByRole('button', {
      name: 'Sao chép link người chơi cho #3 Nguyễn An',
    })
    const secondButton = screen.getByRole('button', {
      name: 'Sao chép link người chơi cho #8 Nguyễn An',
    })
    await user.click(firstButton)

    expect(firstButton).toBeDisabled()
    expect(secondButton).toBeEnabled()
    expect(screen.getByText('Đang sao chép…')).toBeVisible()

    request.resolve({
      sessionId: 'session-1',
      sessionParticipantId: 'participant-a',
      personalAccessToken: 'token-a',
    })
    expect(await screen.findByText('Đã sao chép link')).toBeVisible()
  })

  it('does not write to clipboard after a backend failure and becomes usable again', async () => {
    const user = userWithClipboard()
    getPersonalAccessMock.mockRejectedValueOnce(new Error('secret backend detail'))
    renderActions()

    const button = screen.getByRole('button', {
      name: 'Sao chép link người chơi cho #3 Nguyễn An',
    })
    await user.click(button)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không thể sao chép link. Hãy thử lại.',
    )
    expect(screen.queryByText('secret backend detail')).not.toBeInTheDocument()
    expect(clipboardWrite).not.toHaveBeenCalled()
    await waitFor(() => expect(button).toBeEnabled())
  })

  it('shows a safe reusable failure state when clipboard writing fails', async () => {
    const user = userWithClipboard()
    getPersonalAccessMock.mockResolvedValueOnce({
      sessionId: 'session-1',
      sessionParticipantId: 'participant-a',
      personalAccessToken: 'secret-token',
    })
    clipboardWrite.mockRejectedValueOnce(new Error('clipboard denied'))
    renderActions()

    const button = screen.getByRole('button', {
      name: 'Sao chép link người chơi cho #3 Nguyễn An',
    })
    await user.click(button)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không thể sao chép link. Hãy thử lại.',
    )
    expect(screen.queryByText(/secret-token|clipboard denied/)).not.toBeInTheDocument()
    expect(button).toBeEnabled()
  })
})
