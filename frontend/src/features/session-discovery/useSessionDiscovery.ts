import { useQuery } from '@tanstack/react-query'
import { getSessions } from '../../api/sessionDiscoveryApi'

export const sessionDiscoveryQueryKey = ['sessions'] as const

export function useSessionDiscovery() {
  return useQuery({
    queryKey: sessionDiscoveryQueryKey,
    queryFn: ({ signal }) => getSessions(signal),
    retry: false,
  })
}
