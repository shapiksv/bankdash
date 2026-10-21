import { useAuth } from './useAuth'

/**
 * The logged-in user's id, for query keys: keys include it, so nothing cached for one user is ever shown to the
 * next one in this tab.
 */
export function useUserId(): string | undefined {
  const { state } = useAuth()
  return state.status === 'authenticated' ? state.user.id : undefined
}
