import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { ChunkPreview, Collection, ConversationDetail, ConversationSummary, DocumentView, UploadResult } from './types'

const inFlight = (docs: DocumentView[] | undefined) =>
  docs?.some((d) => d.status !== 'READY' && d.status !== 'FAILED') ?? false

export function useDocuments(collectionId: string | null) {
  return useQuery({
    queryKey: ['documents', collectionId],
    queryFn: () =>
      api.get<DocumentView[]>(`/api/v1/documents${collectionId ? `?collectionId=${collectionId}` : ''}`),
    // Poll while anything is still being processed.
    refetchInterval: (query) => (inFlight(query.state.data) ? 1500 : false),
  })
}

export function useDocument(id: string) {
  return useQuery({
    queryKey: ['document', id],
    queryFn: () => api.get<DocumentView>(`/api/v1/documents/${id}`),
    refetchInterval: (query) => (query.state.data && inFlight([query.state.data]) ? 1500 : false),
  })
}

export function useDocumentChunks(id: string, enabled: boolean) {
  return useQuery({
    queryKey: ['document', id, 'chunks'],
    queryFn: () => api.get<ChunkPreview[]>(`/api/v1/documents/${id}/chunks`),
    enabled,
  })
}

export function useCollections() {
  return useQuery({ queryKey: ['collections'], queryFn: () => api.get<Collection[]>('/api/v1/collections') })
}

export function useUpload() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, collectionId }: { file: File; collectionId: string | null }) => {
      const form = new FormData()
      form.append('file', file)
      if (collectionId) form.append('collectionId', collectionId)
      return api.post<UploadResult>('/api/v1/documents', form)
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['documents'] })
      void queryClient.invalidateQueries({ queryKey: ['collections'] })
    },
  })
}

export function useConversations() {
  return useQuery({ queryKey: ['conversations'], queryFn: () => api.get<ConversationSummary[]>('/api/v1/conversations') })
}

export function useConversation(id: string | undefined) {
  return useQuery({
    queryKey: ['conversation', id],
    queryFn: () => api.get<ConversationDetail>(`/api/v1/conversations/${id}`),
    enabled: !!id,
  })
}

/** Opens the original file at a page; the URL is short-lived so it is fetched on demand. */
export async function openDocumentPage(documentId: string, page?: number) {
  const tab = window.open('', '_blank')
  const { url } = await api.get<{ url: string }>(`/api/v1/documents/${documentId}/file`)
  const target = page ? `${url}#page=${page}` : url
  if (tab) tab.location.href = target
  else window.location.href = target
}
