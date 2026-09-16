import type { PlayerResponse } from '../api/contracts'

export function playerIdentityLabel(player: PlayerResponse): string {
  return `${player.playerCode} · ${player.displayName}`
}

export function matchesPlayerIdentitySearch(
  player: PlayerResponse,
  search: string,
): boolean {
  const normalizedSearch = search.trim().toLocaleLowerCase('vi-VN')
  if (normalizedSearch.length === 0) {
    return true
  }
  return player.displayName
    .toLocaleLowerCase('vi-VN')
    .includes(normalizedSearch)
    || player.playerCode
      .toLocaleLowerCase('vi-VN')
      .includes(normalizedSearch)
}
