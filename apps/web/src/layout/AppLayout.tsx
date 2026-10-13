import { useState } from 'react'
import { Outlet, useLocation } from 'react-router'
import { navItems } from '../navigation'
import { Header } from './Header'
import { Sidebar } from './Sidebar'

export function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { pathname } = useLocation()
  const title = navItems.find((item) => item.path === pathname)?.title ?? 'Not found'

  return (
    <div className="min-h-screen">
      <Sidebar open={menuOpen} onNavigate={() => setMenuOpen(false)} />
      {menuOpen && (
        <div
          aria-hidden
          onClick={() => setMenuOpen(false)}
          className="fixed inset-0 z-20 bg-title/30 lg:hidden"
        />
      )}
      <div className="lg:pl-64">
        <Header title={title} onMenuClick={() => setMenuOpen(true)} />
        <main className="p-6 lg:p-10">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
