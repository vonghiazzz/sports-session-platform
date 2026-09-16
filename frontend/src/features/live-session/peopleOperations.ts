import type { ParticipantView } from './liveSessionModel'

export function normalizePlayerSearch(value: string): string {
  return value
    .trim()
    .toLocaleLowerCase('vi-VN')
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replaceAll('đ', 'd')
}

export function filterParticipantsByName(
  participants: readonly ParticipantView[],
  search: string,
): readonly ParticipantView[] {
  const normalizedSearch = normalizePlayerSearch(search)
  if (normalizedSearch.length === 0) {
    return participants
  }
  return participants.filter((participant) =>
    normalizePlayerSearch(participant.displayName).includes(normalizedSearch),
  )
}
