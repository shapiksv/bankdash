import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './useAuth'

/** Guards the app: anonymous users go to /login and come back here afterwards. */
export function RequireAuth() {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') {
    return (
      <div role="status" className="grid min-h-screen place-items-center text-muted">
        Loading…
      </div>
    )
  }
  if (state.status === 'anonymous') {
    const from = location.pathname + location.search
    const search = from === '/' ? '' : `?from=${encodeURIComponent(from)}`
    return <Navigate to={`/login${search}`} replace />
  }
  return <Outlet />
}
