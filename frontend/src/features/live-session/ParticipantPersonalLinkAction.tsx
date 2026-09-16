import { useMutation } from '@tanstack/react-query'
import {
  Component,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { getSessionParticipantPersonalAccess } from '../../api/liveSessionApi'
import { buildPlayerSessionUrl } from '../../lib/playerSessionAccessUrl'

type PersonalAccessAction = 'copy' | 'qr'
type Feedback = 'copy-success' | 'error' | null

class QrRenderBoundary extends Component<
  { readonly children: ReactNode },
  { readonly failed: boolean }
> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  render() {
    if (this.state.failed) {
      return (
        <p className="action-feedback personal-link-error" role="alert">
          Không thể hiển thị QR. Hãy đóng và thử lại.
        </p>
      )
    }
    return this.props.children
  }
}

async function loadPlayerSessionLink(
  sessionId: string,
  sessionParticipantId: string,
): Promise<string> {
  const access = await getSessionParticipantPersonalAccess(
    sessionId,
    sessionParticipantId,
  )
  return buildPlayerSessionUrl(access.personalAccessToken)
}

export function ParticipantPersonalLinkAction({
  sessionId,
  sessionParticipantId,
  participantLabel,
}: {
  readonly sessionId: string
  readonly sessionParticipantId: string
  readonly participantLabel: string
}) {
  const [pendingAction, setPendingAction] = useState<PersonalAccessAction | null>(
    null,
  )
  const [feedback, setFeedback] = useState<Feedback>(null)
  const [qrOpen, setQrOpen] = useState(false)
  const qrTriggerRef = useRef<HTMLButtonElement>(null)
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const accessMutation = useMutation<string, Error>({
    mutationFn: () => loadPlayerSessionLink(sessionId, sessionParticipantId),
    retry: false,
  })
  const personalUrl = accessMutation.data ?? null

  useEffect(() => {
    if (!qrOpen) {
      return
    }
    closeButtonRef.current?.focus()
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setQrOpen(false)
        qrTriggerRef.current?.focus()
      }
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [qrOpen])

  const getPersonalUrl = async (): Promise<string> =>
    personalUrl ?? accessMutation.mutateAsync()

  const copyLink = async () => {
    setPendingAction('copy')
    setFeedback(null)
    try {
      const clipboard = navigator.clipboard
      if (clipboard === undefined) {
        throw new Error('Clipboard is unavailable')
      }
      await clipboard.writeText(await getPersonalUrl())
      setFeedback('copy-success')
    } catch {
      setFeedback('error')
    } finally {
      setPendingAction(null)
    }
  }

  const openQr = async () => {
    setPendingAction('qr')
    setFeedback(null)
    try {
      await getPersonalUrl()
      setQrOpen(true)
    } catch {
      setFeedback('error')
    } finally {
      setPendingAction(null)
    }
  }

  const closeQr = () => {
    setQrOpen(false)
    qrTriggerRef.current?.focus()
  }

  const pending = pendingAction !== null
  const dialogTitleId = `personal-qr-title-${sessionParticipantId}`
  const dialogDescriptionId = `personal-qr-description-${sessionParticipantId}`

  return (
    <div className="personal-link-action">
      <div className="action-area personal-link-buttons">
        <button
          className="secondary-action-button"
          type="button"
          aria-label={`Sao chép liên kết người chơi cho ${participantLabel}`}
          disabled={pending}
          onClick={() => void copyLink()}
        >
          {pendingAction === 'copy'
            ? 'Đang sao chép…'
            : 'Sao chép liên kết người chơi'}
        </button>
        <button
          ref={qrTriggerRef}
          className="secondary-action-button"
          type="button"
          aria-label={`QR người chơi cho ${participantLabel}`}
          disabled={pending}
          onClick={() => void openQr()}
        >
          {pendingAction === 'qr' ? 'Đang tải QR…' : 'QR người chơi'}
        </button>
      </div>
      {feedback === 'copy-success' && (
        <p className="personal-link-feedback" role="status">
          Đã sao chép liên kết
        </p>
      )}
      {feedback === 'error' && (
        <p className="action-feedback personal-link-error" role="alert">
          Không thể mở hoặc sao chép liên kết. Hãy thử lại.
        </p>
      )}
      {qrOpen && personalUrl !== null && (
        <div className="personal-qr-overlay">
          <section
            className="personal-qr-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby={dialogTitleId}
            aria-describedby={dialogDescriptionId}
          >
            <h2 id={dialogTitleId}>{participantLabel}</h2>
            <QrRenderBoundary key={personalUrl}>
              <QRCodeSVG
                className="personal-qr-code"
                value={personalUrl}
                size={240}
                level="M"
                marginSize={2}
                role="img"
                title={`QR liên kết người chơi ${participantLabel}`}
              />
            </QrRenderBoundary>
            <p id={dialogDescriptionId}>Quét để mở trang cá nhân</p>
            <div className="action-area personal-qr-actions">
              <button
                className="primary-action-button"
                type="button"
                disabled={pending}
                onClick={() => void copyLink()}
              >
                {pendingAction === 'copy'
                  ? 'Đang sao chép…'
                  : 'Sao chép liên kết'}
              </button>
              <button
                ref={closeButtonRef}
                className="secondary-action-button"
                type="button"
                onClick={closeQr}
              >
                Đóng
              </button>
            </div>
          </section>
        </div>
      )}
    </div>
  )
}
