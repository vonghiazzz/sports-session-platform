import { useMutation } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import type { GlobalMatchmakingGenerationResponse } from '../../api/contracts'
import { HttpError } from '../../api/http'
import { generateGlobalMatchmakingPreview } from '../../api/matchmakingApi'

interface GlobalMatchmakingRecommendationState {
  readonly preview: GlobalMatchmakingGenerationResponse | null
  readonly generate: () => Promise<void>
  readonly dismiss: () => void
  readonly isGenerating: boolean
  readonly errorMessage: string | null
}

function generateFailureMessage(error: unknown): string {
  if (!(error instanceof HttpError)) {
    return 'Mất kết nối khi tạo đề xuất toàn phiên. Đề xuất trước đó, nếu có, vẫn được giữ lại.'
  }

  if (error.status === 409) {
    return 'Không thể tạo đề xuất toàn phiên vì trạng thái phiên đã thay đổi.'
  }

  if (error.status === 404) {
    return 'Phiên không còn khả dụng để tạo đề xuất toàn phiên.'
  }

  return 'Không thể tạo đề xuất toàn phiên lúc này. Hãy thử lại sau.'
}

export function useGlobalMatchmakingRecommendation(
  sessionId: string,
): GlobalMatchmakingRecommendationState {
  const generateInFlight = useRef(false)
  const [previewState, setPreviewState] = useState<{
    readonly sessionId: string
    readonly value: GlobalMatchmakingGenerationResponse
  } | null>(null)
  const [errorState, setErrorState] = useState<{
    readonly sessionId: string
    readonly message: string
  } | null>(null)

  const generateMutation = useMutation({
    mutationKey: ['generateGlobalMatchmakingPreview', sessionId],
    mutationFn: () => generateGlobalMatchmakingPreview(sessionId),
    retry: false,
  })

  const generate = useCallback(async () => {
    if (generateInFlight.current) {
      return
    }

    generateInFlight.current = true
    setErrorState(null)

    try {
      const result = await generateMutation.mutateAsync()
      setPreviewState({ sessionId, value: result })
    } catch (error) {
      setErrorState({
        sessionId,
        message: generateFailureMessage(error),
      })
    } finally {
      generateInFlight.current = false
    }
  }, [generateMutation, sessionId])

  const dismiss = useCallback(() => {
    setPreviewState(null)
    setErrorState(null)
  }, [])

  return {
    preview:
      previewState?.sessionId === sessionId ? previewState.value : null,
    generate,
    dismiss,
    isGenerating: generateMutation.isPending,
    errorMessage:
      errorState?.sessionId === sessionId ? errorState.message : null,
  }
}
