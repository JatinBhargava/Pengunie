import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext'

const nav = [
  { to: '/chat', label: 'Ask', icon: 'M8 10h8M8 14h5m-9 6 2.5-3H18a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v14Z' },
  { to: '/documents', label: 'Documents', icon: 'M7 3h7l5 5v13H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm7 0v5h5' },
  { to: '/search', label: 'Search', icon: 'm21 21-4.3-4.3M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z' },
  { to: '/profile', label: 'Profile', icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9a7 7 0 0 1 14 0' },
]

export function Layout() {
  const { state, logout } = useAuth()
  return (
    <div className="flex min-h-dvh flex-col md:flex-row">
      <aside className="flex shrink-0 items-center gap-1 border-b border-zinc-200 bg-white px-3 py-2 md:w-56 md:flex-col md:items-stretch md:border-r md:border-b-0 md:px-3 md:py-5 dark:border-zinc-800 dark:bg-zinc-900">
        <div className="mr-2 flex items-center gap-2 px-2 md:mr-0 md:mb-6">
          <img src="/favicon.svg" alt="" className="size-7" />
          <span className="hidden text-sm font-semibold md:inline">Intelligence OS</span>
        </div>
        <nav className="flex flex-1 gap-1 overflow-x-auto md:flex-col">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm font-medium whitespace-nowrap transition-colors ${
                  isActive
                    ? 'bg-accent-50 text-accent-700 dark:bg-accent-500/15 dark:text-accent-100'
                    : 'text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800'
                }`
              }
            >
              <svg viewBox="0 0 24 24" className="size-4.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d={item.icon} />
              </svg>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="hidden border-t border-zinc-200 pt-4 md:block dark:border-zinc-800">
          <p className="truncate px-2.5 text-sm font-medium">{state.user?.displayName}</p>
          <p className="truncate px-2.5 text-xs text-zinc-500">{state.user?.email}</p>
          <button onClick={() => void logout()} className="mt-2 px-2.5 text-xs text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100">
            Sign out
          </button>
        </div>
      </aside>
      <main className="min-w-0 flex-1">
        <Outlet />
      </main>
    </div>
  )
}
