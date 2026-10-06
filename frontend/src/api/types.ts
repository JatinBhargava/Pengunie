export interface Profile {
  id: string
  email: string
  displayName: string
  timezone: string
  locale: string
}

export interface Session {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: Profile
}

export type DocumentStatus = 'UPLOADED' | 'PARSING' | 'EMBEDDING' | 'READY' | 'FAILED'

export interface DocumentView {
  id: string
  collectionId: string | null
  title: string
  filename: string
  mimeType: string
  sizeBytes: number
  status: DocumentStatus
  pageCount: number | null
  chunkCount: number | null
  error: string | null
  createdAt: string
  updatedAt: string
}

export interface UploadResult {
  document: DocumentView
  duplicate: boolean
}

export interface Collection {
  id: string
  name: string
  documentCount: number
  createdAt: string
}

export interface ChunkPreview {
  id: string
  ordinal: number
  pageStart: number
  pageEnd: number
  heading: string | null
  content: string
  tokenCount: number
}

export type SearchMode = 'SEMANTIC' | 'KEYWORD'

export interface SearchResult {
  chunkId: string
  documentId: string
  documentTitle: string
  pageStart: number
  pageEnd: number
  heading: string | null
  snippet: string
  score: number
}

export interface SearchResponse {
  mode: SearchMode
  results: SearchResult[]
  tookMs: number
}

export type Grounding = 'GROUNDED' | 'NOT_FOUND' | 'UNVERIFIED'

export interface Citation {
  sourceId: number
  chunkId: string
  documentId: string
  documentTitle: string
  pageStart: number
  pageEnd: number
  quote: string
}

export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  grounding: Grounding | null
  citations: Citation[]
  createdAt: string
}

export interface ConversationSummary {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface ConversationDetail {
  id: string
  title: string
  messages: Message[]
}

export interface ChatTurn {
  conversationId: string
  userMessage: Message
  assistantMessage: Message
}
