import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { openDocumentPage, useCollections, useConversation, useConversations } from '../api/hooks'
import type { ChatTurn, Citation, ConversationDetail, Message } from '../api/types'
import { AnswerText } from '../components/AnswerText'
import { ErrorText, pageLabel } from '../components/ui'

export function ChatPage() {
  const { conversationId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const conversations = useConversations()
  const conversation = useConversation(conversationId)
  const collections = useCollections()
  const [draft, setDraft] = useState('')
  const [collectionId, setCollectionId] = useState('')
  const [pending, setPending] = useState<string | null>(null)
  const [selected, setSelected] = useState<Citation | null>(null)
  const bottom = useRef<HTMLDivElement>(null)

  const send = useMutation({
    mutationFn: (message: string) =>
      api.post<ChatTurn>('/api/v1/chat', { conversationId, message, collectionId: collectionId || null }),
    onMutate: (message) => setPending(message),
    onSuccess: (turn) => {
      queryClient.setQueryData<ConversationDetail>(['conversation', turn.conversationId], (old) => ({
        id: turn.conversationId,
        title: old?.title ?? turn.userMessage.content,
        messages: [...(old?.messages ?? []), turn.userMessage, turn.assistantMessage],
      }))
      void queryClient.invalidateQueries({ queryKey: ['conversations'] })
      if (turn.conversationId !== conversationId) navigate(`/chat/${turn.conversationId}`)
    },
    onSettled: () => setPending(null),
  })

  const messages = conversation.data?.messages ?? []
  useEffect(() => bottom.current?.scrollIntoView({ behavior: 'smooth' }), [messages.length, pending])

  function submit(e?: FormEvent) {
    e?.preventDefault()
    const text = draft.trim()
    if (!text || send.isPending) return
    setDraft('')
    send.mutate(text)
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) submit()
  }

  return (
    <div className="flex h-dvh md:h-dvh">
      <aside className="hidden w-64 shrink-0 flex-col border-r border-zinc-200 lg:flex dark:border-zinc-800">
        <div className="p-3">
          <Link
            to="/chat"
            onClick={() => setSelected(null)}
            className="block rounded-lg border border-zinc-200 px-3 py-2 text-sm font-medium hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
          >
            + New question
          </Link>
        </div>
        <nav className="flex-1 space-y-0.5 overflow-y-auto px-3 pb-3">
          {conversations.data?.map((c) => (
            <Link
              key={c.id}
              to={`/chat/${c.id}`}
              className={`block truncate rounded-lg px-3 py-2 text-sm ${
                c.id === conversationId ? 'bg-zinc-200/70 font-medium dark:bg-zinc-800' : 'text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800/60'
              }`}
            >
              {c.title}
            </Link>
          ))}
        </nav>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col">
        <div className="flex-1 overflow-y-auto">
          <div className="mx-auto max-w-3xl space-y-6 px-4 py-8">
            {!conversationId && messages.length === 0 && !pending && (
              <div className="py-16 text-center">
                <h1 className="text-2xl font-semibold tracking-tight">Ask your documents</h1>
                <p className="mt-2 text-sm text-zinc-500">
                  Answers come only from what you've uploaded, with citations you can check.
                </p>
              </div>
            )}
            {messages.map((m) => (
              <MessageBubble key={m.id} message={m} onCite={setSelected} />
            ))}
            {pending && (
              <>
                <MessageBubble message={{ id: 'pending', role: 'user', content: pending, grounding: null, citations: [], createdAt: '' }} onCite={() => {}} />
                <p className="animate-pulse text-sm text-zinc-500">Searching your documents…</p>
              </>
            )}
            <ErrorText error={send.error} />
            <div ref={bottom} />
          </div>
        </div>

        <form onSubmit={submit} className="border-t border-zinc-200 bg-white p-3 dark:border-zinc-800 dark:bg-zinc-900">
          <div className="mx-auto max-w-3xl">
            <div className="flex items-end gap-2 rounded-xl border border-zinc-300 bg-white p-2 focus-within:border-accent-500 dark:border-zinc-700 dark:bg-zinc-950">
              <textarea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={onKeyDown}
                rows={1}
                placeholder="Ask about your documents…"
                className="max-h-40 min-h-9 flex-1 resize-none bg-transparent px-2 py-1.5 text-sm outline-none"
              />
              <button
                type="submit"
                disabled={!draft.trim() || send.isPending}
                className="rounded-lg bg-accent-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-40"
              >
                Ask
              </button>
            </div>
            <div className="mt-2 flex items-center gap-2 text-xs text-zinc-500">
              <span>Search in</span>
              <select
                value={collectionId}
                onChange={(e) => setCollectionId(e.target.value)}
                className="rounded border border-zinc-300 bg-transparent px-1 py-0.5 dark:border-zinc-700"
              >
                <option value="">All documents</option>
                {collections.data?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </form>
      </section>

      {selected && <CitationPanel citation={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}

function MessageBubble({ message, onCite }: { message: Message; onCite: (c: Citation) => void }) {
  if (message.role === 'user') {
    return (
      <div className="flex justify-end">
        <p className="max-w-[85%] rounded-2xl rounded-br-md bg-accent-600 px-4 py-2.5 text-sm whitespace-pre-line text-white">
          {message.content}
        </p>
      </div>
    )
  }
  return (
    <div className="space-y-2">
      {message.grounding && message.grounding !== 'GROUNDED' && (
        <span className="inline-block rounded-full bg-zinc-100 px-2 py-0.5 text-[11px] font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
          {message.grounding === 'NOT_FOUND' ? 'Not in your documents' : 'Could not verify'}
        </span>
      )}
      <AnswerText text={message.content} citations={message.citations} onCite={onCite} />
      {message.citations.length > 0 && (
        <div className="flex flex-wrap gap-1.5 pt-1">
          {message.citations.map((c) => (
            <button
              key={`${c.sourceId}-${c.quote}`}
              onClick={() => onCite(c)}
              className="rounded-md border border-zinc-200 px-2 py-1 text-left text-xs text-zinc-600 hover:border-accent-500 hover:text-accent-700 dark:border-zinc-700 dark:text-zinc-400"
            >
              <span className="font-semibold">{c.sourceId}</span> · {c.documentTitle} · {pageLabel(c.pageStart, c.pageEnd)}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function CitationPanel({ citation, onClose }: { citation: Citation; onClose: () => void }) {
  return (
    <aside className="fixed inset-x-0 bottom-0 z-10 max-h-[60dvh] overflow-y-auto border-t border-zinc-200 bg-white p-5 shadow-xl md:static md:inset-auto md:max-h-none md:w-80 md:shrink-0 md:border-t-0 md:border-l md:shadow-none dark:border-zinc-800 dark:bg-zinc-900">
      <div className="mb-4 flex items-start justify-between gap-2">
        <div>
          <p className="text-xs font-medium tracking-wide text-zinc-500 uppercase">Source {citation.sourceId}</p>
          <Link to={`/documents/${citation.documentId}`} className="text-sm font-semibold hover:underline">
            {citation.documentTitle}
          </Link>
          <p className="text-xs text-zinc-500">{pageLabel(citation.pageStart, citation.pageEnd)}</p>
        </div>
        <button onClick={onClose} aria-label="Close" className="rounded p-1 text-zinc-500 hover:bg-zinc-100 dark:hover:bg-zinc-800">
          ✕
        </button>
      </div>
      <blockquote className="border-l-2 border-accent-500 pl-3 text-sm text-zinc-700 italic dark:text-zinc-300">
        “{citation.quote}”
      </blockquote>
      <button
        onClick={() => void openDocumentPage(citation.documentId, citation.pageStart)}
        className="mt-5 w-full rounded-lg bg-zinc-900 px-3 py-2 text-sm font-medium text-white hover:bg-zinc-700 dark:bg-zinc-100 dark:text-zinc-900"
      >
        Open page {citation.pageStart}
      </button>
    </aside>
  )
}
