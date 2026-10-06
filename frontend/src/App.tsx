import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { useAuth } from './auth/AuthContext'
import { Layout } from './components/Layout'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { ChatPage } from './pages/ChatPage'
import { DocumentDetailPage } from './pages/DocumentDetailPage'
import { DocumentsPage } from './pages/DocumentsPage'
import { ProfilePage } from './pages/ProfilePage'
import { SearchPage } from './pages/SearchPage'

function RequireAuth({ children }: { children: ReactNode }) {
  const { state } = useAuth()
  if (state.status === 'loading') return <div className="p-8 text-sm text-zinc-500">Loading…</div>
  if (state.status === 'anonymous') return <Navigate to="/login" replace />
  return children
}

function AnonymousOnly({ children }: { children: ReactNode }) {
  const { state } = useAuth()
  if (state.status === 'loading') return null
  if (state.status === 'authenticated') return <Navigate to="/chat" replace />
  return children
}

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<AnonymousOnly><LoginPage /></AnonymousOnly>} />
        <Route path="/register" element={<AnonymousOnly><RegisterPage /></AnonymousOnly>} />
        <Route element={<RequireAuth><Layout /></RequireAuth>}>
          <Route index element={<Navigate to="/chat" replace />} />
          <Route path="/chat" element={<ChatPage />} />
          <Route path="/chat/:conversationId" element={<ChatPage />} />
          <Route path="/documents" element={<DocumentsPage />} />
          <Route path="/documents/:id" element={<DocumentDetailPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
