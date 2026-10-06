import { Link, useNavigate, useParams } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { openDocumentPage, useCollections, useDocument, useDocumentChunks } from '../api/hooks'
import { Button, Card, ErrorText, PageHeader, StatusBadge, formatBytes, pageLabel } from '../components/ui'

export function DocumentDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const doc = useDocument(id)
  const chunks = useDocumentChunks(id, doc.data?.status === 'READY')
  const collections = useCollections()

  const remove = useMutation({
    mutationFn: () => api.delete(`/api/v1/documents/${id}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['documents'] })
      navigate('/documents')
    },
  })
  const move = useMutation({
    mutationFn: (collectionId: string | null) => api.patch(`/api/v1/documents/${id}`, { collectionId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['document', id] })
      void queryClient.invalidateQueries({ queryKey: ['documents'] })
      void queryClient.invalidateQueries({ queryKey: ['collections'] })
    },
  })

  if (doc.error) return <div className="p-8"><ErrorText error={doc.error} /></div>
  if (!doc.data) return <div className="p-8 text-sm text-zinc-500">Loading…</div>
  const d = doc.data

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 md:px-8">
      <Link to="/documents" className="text-sm text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100">
        ← Documents
      </Link>
      <div className="mt-3">
        <PageHeader
          title={d.title}
          description={`${d.filename} · ${formatBytes(d.sizeBytes)}${d.pageCount ? ` · ${d.pageCount} pages` : ''}${d.chunkCount ? ` · ${d.chunkCount} chunks` : ''}`}
          actions={
            <div className="flex items-center gap-2">
              <StatusBadge status={d.status} />
              <Button variant="ghost" onClick={() => void openDocumentPage(d.id)}>
                Open file
              </Button>
              <Button
                variant="danger"
                onClick={() => {
                  if (confirm(`Delete “${d.title}”? This removes the file and its index.`)) remove.mutate()
                }}
              >
                Delete
              </Button>
            </div>
          }
        />
      </div>

      <div className="mb-6 flex items-center gap-2 text-sm">
        <span className="text-zinc-500">Collection</span>
        <select
          value={d.collectionId ?? ''}
          onChange={(e) => move.mutate(e.target.value || null)}
          className="rounded-lg border border-zinc-300 bg-white px-2 py-1 dark:border-zinc-700 dark:bg-zinc-900"
        >
          <option value="">None</option>
          {collections.data?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      {d.status === 'FAILED' && (
        <Card className="mb-6 border-red-200 p-4 text-sm text-red-700 dark:border-red-900 dark:text-red-300">{d.error}</Card>
      )}
      {d.status !== 'READY' && d.status !== 'FAILED' && (
        <p className="text-sm text-zinc-500">Processing… this page updates automatically.</p>
      )}

      {chunks.data && (
        <div className="space-y-3">
          <h2 className="text-sm font-semibold text-zinc-500">Indexed chunks</h2>
          {chunks.data.map((c) => (
            <Card key={c.id} className="p-4">
              <div className="mb-2 flex items-center gap-2 text-xs text-zinc-500">
                <span className="font-mono">#{c.ordinal + 1}</span>
                <span>{pageLabel(c.pageStart, c.pageEnd)}</span>
                {c.heading && <span className="truncate">· {c.heading}</span>}
                <span className="ml-auto">{c.tokenCount} tokens</span>
              </div>
              <p className="line-clamp-4 text-sm whitespace-pre-line text-zinc-700 dark:text-zinc-300">{c.content}</p>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
