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

vi.mock('qrcode.react', () => ({
  QRCodeSVG: ({
    value,
    title,
    className,
  }: {
    value: string
    title: string
    className: string
  }) => (
    <svg
      className={className}
      data-testid="personal-qr-code"
      data-qr-value={value}
      role="img"
    >
      <title>{title}</title>
    </svg>
  ),
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
  it('lazily opens the correct duplicate-name QR and reuses its exact URL for Copy', async () => {
    const user = userWithClipboard()
    getPersonalAccessMock.mockImplementation(async (_sessionId, participantId) => ({
      sessionId: 'session-1',
      sessionParticipantId: participantId,
      personalAccessToken:
        participantId === 'participant-a' ? 'token-a' : 'token-b',
    }))
    renderActions()

    expect(getPersonalAccessMock).not.toHaveBeenCalled()
    expect(screen.getAllByText('QR người chơi')).toHaveLength(2)
    expect(screen.getAllByText('Sao chép link người chơi')).toHaveLength(2)

    await user.click(
      screen.getByRole('button', {
        name: 'QR người chơi cho #8 Nguyễn An',
      }),
    )

    expect(getPersonalAccessMock).toHaveBeenLastCalledWith(
      'session-1',
      'participant-b',
    )
    const expectedUrl = new URL(
      '/player-session/token-b',
      window.location.origin,
    ).toString()
    const dialog = await screen.findByRole('dialog', { name: '#8 Nguyễn An' })
    expect(dialog).toHaveTextContent('#8 Nguyễn An')
    expect(dialog).toHaveTextContent('Quét để mở trang cá nhân')
    expect(screen.getByTestId('personal-qr-code')).toHaveAttribute(
      'data-qr-value',
      expectedUrl,
    )
    expect(screen.getByRole('img', { name: 'QR link người chơi #8 Nguyễn An' }))
      .toBeVisible()
    expect(screen.queryByText('token-b')).not.toBeInTheDocument()
    expect(clipboardWrite).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Sao chép link' }))

    expect(getPersonalAccessMock).toHaveBeenCalledOnce()
    expect(clipboardWrite).toHaveBeenLastCalledWith(expectedUrl)
    expect(await screen.findByText('Đã sao chép link')).toBeVisible()
    expect(screen.queryByText('token-b')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Đóng' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(
      screen.getByRole('button', {
        name: 'QR người chơi cho #8 Nguyễn An',
      }),
    ).toHaveFocus()

    await user.click(
      screen.getByRole('button', {
        name: 'QR người chơi cho #3 Nguyễn An',
      }),
    )
    expect(getPersonalAccessMock).toHaveBeenLastCalledWith(
      'session-1',
      'participant-a',
    )
    expect(await screen.findByRole('dialog', { name: '#3 Nguyễn An' }))
      .toBeVisible()
  })

  it('scopes QR token-fetch pending state to the clicked Session Participant', async () => {
    const user = userWithClipboard()
    const request = deferred<{
      sessionId: string
      sessionParticipantId: string
      personalAccessToken: string
    }>()
    getPersonalAccessMock.mockReturnValueOnce(request.promise)
    renderActions()

    const firstQrButton = screen.getByRole('button', {
      name: 'QR người chơi cho #3 Nguyễn An',
    })
    const firstCopyButton = screen.getByRole('button', {
      name: 'Sao chép link người chơi cho #3 Nguyễn An',
    })
    const secondQrButton = screen.getByRole('button', {
      name: 'QR người chơi cho #8 Nguyễn An',
    })
    const secondCopyButton = screen.getByRole('button', {
      name: 'Sao chép link người chơi cho #8 Nguyễn An',
    })
    await user.click(firstQrButton)

    expect(firstQrButton).toBeDisabled()
    expect(firstCopyButton).toBeDisabled()
    expect(secondQrButton).toBeEnabled()
    expect(secondCopyButton).toBeEnabled()
    expect(screen.getByText('Đang tải QR…')).toBeVisible()

    request.resolve({
      sessionId: 'session-1',
      sessionParticipantId: 'participant-a',
      personalAccessToken: 'token-a',
    })
    expect(await screen.findByRole('dialog', { name: '#3 Nguyễn An' }))
      .toBeVisible()
  })

  it('does not render a QR after backend failure and allows an explicit retry', async () => {
    const user = userWithClipboard()
    getPersonalAccessMock.mockRejectedValueOnce(new Error('secret backend detail'))
    getPersonalAccessMock.mockResolvedValueOnce({
      sessionId: 'session-1',
      sessionParticipantId: 'participant-a',
      personalAccessToken: 'token-a',
    })
    renderActions()

    const button = screen.getByRole('button', {
      name: 'QR người chơi cho #3 Nguyễn An',
    })
    await user.click(button)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không thể mở hoặc sao chép link. Hãy thử lại.',
    )
    expect(screen.queryByText('secret backend detail')).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(clipboardWrite).not.toHaveBeenCalled()
    await waitFor(() => expect(button).toBeEnabled())

    await user.click(button)
    expect(await screen.findByRole('dialog', { name: '#3 Nguyễn An' }))
      .toBeVisible()
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
      'Không thể mở hoặc sao chép link. Hãy thử lại.',
    )
    expect(screen.queryByText(/secret-token|clipboard denied/)).not.toBeInTheDocument()
    expect(button).toBeEnabled()
  })

  it('closes the QR dialog with Escape and restores trigger focus', async () => {
    const user = userWithClipboard()
    getPersonalAccessMock.mockResolvedValueOnce({
      sessionId: 'session-1',
      sessionParticipantId: 'participant-a',
      personalAccessToken: 'token-a',
    })
    renderActions()
    const trigger = screen.getByRole('button', {
      name: 'QR người chơi cho #3 Nguyễn An',
    })

    await user.click(trigger)
    expect(await screen.findByRole('dialog', { name: '#3 Nguyễn An' }))
      .toBeVisible()
    expect(screen.getByRole('button', { name: 'Đóng' })).toHaveFocus()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })
})
