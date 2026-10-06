import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../api/client'
import { openDocumentPage, useCollections } from '../api/hooks'
import type { SearchMode, SearchResponse } from '../api/types'
import { Highlighted } from '../components/Highlighted'
import { Button, Card, ErrorText, Input, PageHeader, pageLabel } from '../components/ui'

export function SearchPage() {
  const [query, setQuery] = useState('')
  const [mode, setMode] = useState<SearchMode>('SEMANTIC')
  const [collectionId, setCollectionId] = useState('')
  const collections = useCollections()
  const search = useMutation({
    mutationFn: (body: { query: string; mode: SearchMode; collectionId: string | null }) =>
      api.post<SearchResponse>('/api/v1/search', { ...body, topK: 15 }),
  })

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (query.trim()) search.mutate({ query: query.trim(), mode, collectionId: collectionId || null })
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 md:px-8">
      <PageHeader
        title="Search"
        description="Semantic search finds passages by meaning; keyword search finds exact terms like policy numbers."
      />
      <form onSubmit={onSubmit} className="mb-6 space-y-3">
        <div className="flex gap-2">
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="e.g. when does the car insurance renew?" autoFocus />
          <Button type="submit" disabled={search.isPending}>
            Search
          </Button>
        </div>
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <div className="inline-flex rounded-lg bg-zinc-100 p-0.5 dark:bg-zinc-800">
            {(['SEMANTIC', 'KEYWORD'] as const).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => setMode(m)}
                className={`rounded-md px-3 py-1 ${mode === m ? 'bg-white shadow-sm dark:bg-zinc-700' : 'text-zinc-500'}`}
              >
                {m === 'SEMANTIC' ? 'Semantic' : 'Keyword'}
              </button>
            ))}
          </div>
          <select
            value={collectionId}
            onChange={(e) => setCollectionId(e.target.value)}
            className="rounded-lg border border-zinc-300 bg-white px-2 py-1 dark:border-zinc-700 dark:bg-zinc-900"
          >
            <option value="">All collections</option>
            {collections.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          {search.data && (
            <span className="text-zinc-500">
              {search.data.results.length} results · {search.data.tookMs} ms
            </span>
          )}
        </div>
      </form>

      <ErrorText error={search.error} />
      {search.data?.results.length === 0 && <p className="py-12 text-center text-sm text-zinc-500">No matching passages.</p>}
      <div className="space-y-3">
        {search.data?.results.map((r) => (
          <Card key={r.chunkId} className="p-4">
            <div className="mb-1.5 flex flex-wrap items-center gap-2 text-xs text-zinc-500">
              <Link to={`/documents/${r.documentId}`} className="font-medium text-zinc-800 hover:underline dark:text-zinc-200">
                {r.documentTitle}
              </Link>
              <button onClick={() => void openDocumentPage(r.documentId, r.pageStart)} className="text-accent-600 hover:underline">
                {pageLabel(r.pageStart, r.pageEnd)}
              </button>
              {r.heading && <span className="truncate">· {r.heading}</span>}
              <span className="ml-auto font-mono">{r.score.toFixed(3)}</span>
            </div>
            <p className="text-sm whitespace-pre-line text-zinc-700 dark:text-zinc-300">
              <Highlighted text={r.snippet} />
              {search.data.mode === 'SEMANTIC' && r.snippet.length >= 320 ? '…' : ''}
            </p>
          </Card>
        ))}
      </div>
    </div>
  )
}
