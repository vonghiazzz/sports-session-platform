export function buildPlayerSessionUrl(token: string): string {
  const path = `/player-session/${encodeURIComponent(token)}`
  return new URL(path, window.location.origin).toString()
}
