import { LogOut } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { initials, useAvatarUrl, useProfile } from '../profile/queries'

/**
 * The avatar in the header: the uploaded image, else the name's initials. Right after signup the profile may
 * not exist yet (it arrives through Kafka), so the email's initial stands in until it does.
 */
export function UserMenu() {
  const { state, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const container = useRef<HTMLDivElement>(null)
  const email = state.status === 'authenticated' ? state.user.email : ''
  const { data: profile } = useProfile()
  const avatarUrl = useAvatarUrl(profile)

  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent | KeyboardEvent) => {
      if (event instanceof KeyboardEvent ? event.key === 'Escape' : !container.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', close)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', close)
    }
  }, [open])

  async function onLogout() {
    setOpen(false)
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div ref={container} className="relative">
      <button
        type="button"
        aria-label="Account menu"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="grid size-12 place-items-center overflow-hidden rounded-full bg-primary/10 font-semibold text-primary uppercase hover:bg-primary/20"
      >
        {avatarUrl ? (
          <img src={avatarUrl} alt="" className="size-full object-cover" />
        ) : (
          <span aria-hidden>{initials(profile?.fullName, email)}</span>
        )}
      </button>
      {open && (
        <div
          role="menu"
          aria-label="Account"
          className="absolute right-0 top-14 z-20 w-64 rounded-[25px] border border-border bg-surface p-2 shadow-lg"
        >
          <div className="px-4 py-3">
            {profile && <p className="truncate font-medium text-title">{profile.fullName}</p>}
            <p className="truncate text-sm text-muted">{email}</p>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={onLogout}
            className="flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-left text-title hover:bg-canvas"
          >
            <LogOut className="size-5" aria-hidden />
            Log out
          </button>
        </div>
      )}
    </div>
  )
}
