import { useRef, useState, type DragEvent } from 'react'
import { Link } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { useCollections, useDocuments, useUpload } from '../api/hooks'
import type { Collection } from '../api/types'
import { Button, Card, ErrorText, Input, PageHeader, StatusBadge, formatBytes } from '../components/ui'

const ACCEPT = '.pdf,.txt,.md,.docx,application/pdf,text/plain,text/markdown'

export function DocumentsPage() {
  const [collectionId, setCollectionId] = useState<string | null>(null)
  const documents = useDocuments(collectionId)
  const collections = useCollections()
  const upload = useUpload()
  const fileInput = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [notices, setNotices] = useState<string[]>([])

  async function uploadFiles(files: FileList | File[]) {
    setNotices([])
    for (const file of Array.from(files)) {
      try {
        const result = await upload.mutateAsync({ file, collectionId })
        if (result.duplicate) setNotices((n) => [...n, `“${file.name}” is already in your library.`])
      } catch (err) {
        setNotices((n) => [...n, `${file.name}: ${err instanceof Error ? err.message : 'upload failed'}`])
      }
    }
  }

  function onDrop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    if (e.dataTransfer.files.length) void uploadFiles(e.dataTransfer.files)
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 md:px-8">
      <PageHeader title="Documents" description="Upload PDFs, text, Markdown or Word files. They're parsed, indexed and only visible to you." />

      <div className="mb-6 flex flex-wrap items-center gap-2">
        <CollectionPill active={collectionId === null} onClick={() => setCollectionId(null)} label="All" />
        {collections.data?.map((c) => (
          <CollectionPill
            key={c.id}
            active={collectionId === c.id}
            onClick={() => setCollectionId(c.id)}
            label={`${c.name} · ${c.documentCount}`}
          />
        ))}
        <NewCollection />
      </div>

      <div
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={`mb-6 flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-10 text-center transition-colors ${
          dragging ? 'border-accent-500 bg-accent-50 dark:bg-accent-500/10' : 'border-zinc-300 dark:border-zinc-700'
        }`}
      >
        <p className="text-sm font-medium">Drop files here</p>
        <p className="mt-1 text-xs text-zinc-500">PDF, TXT, MD, DOCX · up to 25 MB</p>
        <Button className="mt-4" onClick={() => fileInput.current?.click()} disabled={upload.isPending}>
          {upload.isPending ? 'Uploading…' : 'Choose files'}
        </Button>
        <input
          ref={fileInput}
          type="file"
          multiple
          accept={ACCEPT}
          className="hidden"
          onChange={(e) => {
            if (e.target.files) void uploadFiles(e.target.files)
            e.target.value = ''
          }}
        />
        {notices.map((n) => (
          <p key={n} className="mt-2 text-xs text-zinc-600 dark:text-zinc-400">
            {n}
          </p>
        ))}
      </div>

      <ErrorText error={documents.error} />
      {documents.data && documents.data.length === 0 && (
        <p className="py-12 text-center text-sm text-zinc-500">No documents yet. Upload one to get started.</p>
      )}
      {documents.data && documents.data.length > 0 && (
        <Card className="divide-y divide-zinc-200 dark:divide-zinc-800">
          {documents.data.map((doc) => (
            <Link
              key={doc.id}
              to={`/documents/${doc.id}`}
              className="flex items-center gap-4 px-4 py-3 transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/50"
            >
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{doc.title}</p>
                <p className="truncate text-xs text-zinc-500">
                  {doc.filename} · {formatBytes(doc.sizeBytes)}
                  {doc.pageCount ? ` · ${doc.pageCount} page${doc.pageCount === 1 ? '' : 's'}` : ''}
                  {doc.status === 'FAILED' && doc.error ? ` · ${doc.error}` : ''}
                </p>
              </div>
              <StatusBadge status={doc.status} />
            </Link>
          ))}
        </Card>
      )}
    </div>
  )
}

function CollectionPill({ active, label, onClick }: { active: boolean; label: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full px-3 py-1 text-sm transition-colors ${
        active
          ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
          : 'bg-white text-zinc-700 ring-1 ring-zinc-200 hover:bg-zinc-100 dark:bg-zinc-900 dark:text-zinc-300 dark:ring-zinc-700'
      }`}
    >
      {label}
    </button>
  )
}

function NewCollection() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const create = useMutation({
    mutationFn: (n: string) => api.post<Collection>('/api/v1/collections', { name: n }),
    onSuccess: () => {
      setName('')
      setOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['collections'] })
    },
  })
  if (!open) {
    return (
      <Button variant="ghost" onClick={() => setOpen(true)} className="py-1">
        + Collection
      </Button>
    )
  }
  return (
    <form
      className="flex items-center gap-2"
      onSubmit={(e) => {
        e.preventDefault()
        if (name.trim()) create.mutate(name.trim())
      }}
    >
      <Input autoFocus placeholder="Collection name" value={name} onChange={(e) => setName(e.target.value)} className="h-8 w-44 py-1" />
      <Button type="submit" className="py-1" disabled={create.isPending}>
        Add
      </Button>
      <ErrorText error={create.error} />
    </form>
  )
}
