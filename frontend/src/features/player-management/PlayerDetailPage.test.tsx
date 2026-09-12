import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PlayerResponse } from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  getPlayer,
  updatePlayerSkillLevel,
} from '../../api/playerApi'
import { PlayerDetailPage } from './PlayerDetailPage'

vi.mock('../../api/playerApi', () => ({
  getPlayers: vi.fn(),
  getPlayer: vi.fn(),
  updatePlayerSkillLevel: vi.fn(),
}))

const initialPlayer: PlayerResponse = {
  id: 'player-1',
  displayName: 'Nguyễn An',
  sportProfiles: [{
    id: 'profile-1',
    sport: 'BADMINTON',
    skillLevel: 'INTERMEDIATE',
    rating: {
      ratingValue: 27,
      uncertainty: 8.333333333,
      ratedMatches: 0,
      ratingBasis: 'INITIAL_PRIOR',
      ratingAlgorithmVersion: null,
    },
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  }],
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

const maturePlayer: PlayerResponse = {
  ...initialPlayer,
  sportProfiles: [{
    ...initialPlayer.sportProfiles[0],
    rating: {
      ratingValue: 28.765432109,
      uncertainty: 4.8,
      ratedMatches: 14,
      ratingBasis: 'PERSISTED',
      ratingAlgorithmVersion: 'weng-lin-pl-v1',
    },
  }],
}

const updatedMaturePlayer: PlayerResponse = {
  ...maturePlayer,
  sportProfiles: [{
    ...maturePlayer.sportProfiles[0],
    skillLevel: 'INTERMEDIATE_PLUS',
    updatedAt: '2026-09-02T00:00:00Z',
  }],
}

function deferred<T>() {
  let resolvePromise!: (value: T) => void
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve
  })
  return { promise, resolve: resolvePromise }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/players/player-1']}>
          <Routes>
            <Route path="/players/:playerId" element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    )
  }
  return {
    queryClient,
    user: userEvent.setup(),
    ...render(<PlayerDetailPage />, { wrapper: Wrapper }),
  }
}

describe('PlayerDetailPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getPlayer).mockResolvedValue(initialPlayer)
    vi.mocked(updatePlayerSkillLevel).mockResolvedValue(initialPlayer)
  })

  it('renders Player identity, Skill, initial Rating, uncertainty, and basis', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Nguyễn An' })).toBeInTheDocument()
    expect(screen.getByText('Cầu lông')).toBeInTheDocument()
    const currentSkill = screen.getByText('Trình hiện tại').parentElement
    expect(currentSkill).not.toBeNull()
    expect(within(currentSkill!).getByText('TB')).toBeInTheDocument()
    expect(screen.getByText('27,0')).toBeInTheDocument()
    expect(screen.getByText('8,33')).toBeInTheDocument()
    expect(screen.getByText('Chưa có trận được tính Rating')).toBeInTheDocument()
    expect(screen.getByText('Điểm khởi tạo theo trình Host đánh giá')).toBeInTheDocument()
    expect(screen.queryByText('Phiên bản thuật toán Rating')).not.toBeInTheDocument()
  })

  it('renders persisted Rating diagnostics and algorithm version', async () => {
    vi.mocked(getPlayer).mockResolvedValue(maturePlayer)
    renderPage()
    expect(await screen.findByText('28,77')).toBeInTheDocument()
    expect(screen.getByText('14 trận đã tính Rating')).toBeInTheDocument()
    expect(screen.getByText('Rating đã học từ kết quả thi đấu')).toBeInTheDocument()
    expect(screen.getByText('weng-lin-pl-v1')).toBeInTheDocument()
  })

  it('shows exactly six choices with the current Skill selected', async () => {
    renderPage()
    const select = await screen.findByLabelText('Thay đổi trình độ')
    expect(select).toHaveValue('INTERMEDIATE')
    expect(within(select).getAllByRole('option')).toHaveLength(6)
    expect(within(select).getAllByRole('option').map((option) => option.textContent))
      .toEqual(['Yếu', 'Yếu+', 'TB-', 'TB', 'TB+', 'Khá'])
  })

  it('does not allow submitting an unchanged Skill', async () => {
    renderPage()
    expect(await screen.findByRole('button', { name: 'Lưu trình độ' })).toBeDisabled()
    expect(updatePlayerSkillLevel).not.toHaveBeenCalled()
  })

  it('uses the exact PUT input and accepts the backend response as authoritative', async () => {
    vi.mocked(getPlayer).mockResolvedValue(maturePlayer)
    vi.mocked(updatePlayerSkillLevel).mockResolvedValue(updatedMaturePlayer)
    const { user, queryClient } = renderPage()
    queryClient.setQueryData(['players', ''], [maturePlayer])

    await user.selectOptions(
      await screen.findByLabelText('Thay đổi trình độ'),
      'INTERMEDIATE_PLUS',
    )
    await user.click(screen.getByRole('button', { name: 'Lưu trình độ' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Đã cập nhật')
    expect(updatePlayerSkillLevel).toHaveBeenCalledWith(
      'player-1',
      'BADMINTON',
      { skillLevel: 'INTERMEDIATE_PLUS' },
    )
    expect(screen.getByText('28,77')).toBeInTheDocument()
    expect(screen.getByText('14 trận đã tính Rating')).toBeInTheDocument()
    expect(queryClient.getQueryState(['players', ''])?.isInvalidated).toBe(true)
  })

  it('guards duplicate submits while the first PUT is pending', async () => {
    const pending = deferred<PlayerResponse>()
    vi.mocked(updatePlayerSkillLevel).mockReturnValue(pending.promise)
    const { user } = renderPage()
    await user.selectOptions(
      await screen.findByLabelText('Thay đổi trình độ'),
      'INTERMEDIATE_PLUS',
    )
    const submit = screen.getByRole('button', { name: 'Lưu trình độ' })
    await user.click(submit)
    await user.click(submit)
    expect(updatePlayerSkillLevel).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Đang cập nhật…' })).toBeDisabled()
    pending.resolve(updatedMaturePlayer)
  })

  it('keeps Player data and a scoped error after a known API failure', async () => {
    vi.mocked(updatePlayerSkillLevel).mockRejectedValue(
      new HttpError(400, 'invalid'),
    )
    const { user } = renderPage()
    await user.selectOptions(
      await screen.findByLabelText('Thay đổi trình độ'),
      'GOOD',
    )
    await user.click(screen.getByRole('button', { name: 'Lưu trình độ' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('không hợp lệ')
    expect(screen.getByRole('heading', { name: 'Nguyễn An' })).toBeInTheDocument()
    expect(screen.getByLabelText('Thay đổi trình độ')).not.toBeDisabled()
  })

  it('reconciles an ambiguous transport failure before reporting success', async () => {
    vi.mocked(getPlayer)
      .mockResolvedValueOnce(maturePlayer)
      .mockResolvedValueOnce(updatedMaturePlayer)
    vi.mocked(updatePlayerSkillLevel).mockRejectedValue(new TypeError('offline'))
    const { user } = renderPage()
    await user.selectOptions(
      await screen.findByLabelText('Thay đổi trình độ'),
      'INTERMEDIATE_PLUS',
    )
    await user.click(screen.getByRole('button', { name: 'Lưu trình độ' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Đã xác nhận')
    expect(getPlayer).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('makes a transport failure safe to retry when reconciliation shows the Skill was not applied', async () => {
    const reconciliation = deferred<PlayerResponse>()
    vi.mocked(getPlayer)
      .mockResolvedValueOnce(initialPlayer)
      .mockReturnValueOnce(reconciliation.promise)
    vi.mocked(updatePlayerSkillLevel).mockRejectedValue(new TypeError('offline'))
    const { user } = renderPage()
    await user.selectOptions(
      await screen.findByLabelText('Thay đổi trình độ'),
      'INTERMEDIATE_PLUS',
    )
    await user.click(screen.getByRole('button', { name: 'Lưu trình độ' }))

    expect(await screen.findByRole('button', { name: 'Đang cập nhật…' }))
      .toBeDisabled()
    expect(screen.getByLabelText('Thay đổi trình độ')).toBeDisabled()
    reconciliation.resolve(initialPlayer)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Hệ thống chưa áp dụng trình độ đã chọn',
    )
    expect(screen.queryByRole('button', { name: 'Kiểm tra lại dữ liệu' }))
      .not.toBeInTheDocument()
    expect(screen.getByLabelText('Thay đổi trình độ')).not.toBeDisabled()
    expect(screen.getByRole('button', { name: 'Lưu trình độ' })).not.toBeDisabled()
    expect(updatePlayerSkillLevel).toHaveBeenCalledOnce()
  })

  it('blocks resubmit until an unknown outcome can be checked successfully', async () => {
    vi.mocked(getPlayer)
      .mockResolvedValueOnce(initialPlayer)
      .mockRejectedValueOnce(new TypeError('offline'))
    vi.mocked(updatePlayerSkillLevel).mockRejectedValue(new TypeError('offline'))
    const { user } = renderPage()
    await user.selectOptions(
      await screen.findByLabelText('Thay đổi trình độ'),
      'INTERMEDIATE_PLUS',
    )
    await user.click(screen.getByRole('button', { name: 'Lưu trình độ' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('chưa xác định')
    expect(screen.getByLabelText('Thay đổi trình độ')).toBeDisabled()
    expect(screen.getByText('27,0')).toBeInTheDocument()

    vi.mocked(getPlayer).mockResolvedValue(updatedMaturePlayer)
    await user.click(screen.getByRole('button', { name: 'Kiểm tra lại dữ liệu' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Đã xác nhận')
    expect(screen.getByLabelText('Thay đổi trình độ')).not.toBeDisabled()
    expect(updatePlayerSkillLevel).toHaveBeenCalledOnce()
  })

  it('shows a clear 404 state and navigation back', async () => {
    vi.mocked(getPlayer).mockRejectedValue(new HttpError(404, 'missing'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy người chơi.')
    expect(screen.getByRole('link', { name: 'Về danh sách người chơi' }))
      .toHaveAttribute('href', '/players')
  })

  it('shows a retry for other detail failures', async () => {
    vi.mocked(getPlayer)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(initialPlayer)
    const { user } = renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải')
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('heading', { name: 'Nguyễn An' })).toBeInTheDocument()
  })
})
