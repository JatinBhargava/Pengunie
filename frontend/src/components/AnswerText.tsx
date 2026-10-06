import type { Citation } from '../api/types'

/**
 * Renders an answer, turning inline [n] markers into clickable chips — but only for citations the
 * backend validated. Markers pointing at dropped citations are removed rather than shown as links.
 */
export function AnswerText({
  text,
  citations,
  onCite,
}: {
  text: string
  citations: Citation[]
  onCite: (citation: Citation) => void
}) {
  const bySource = new Map(citations.map((c) => [c.sourceId, c]))
  const parts = text.split(/(\[\d+(?:,\s*\d+)*\])/g)
  return (
    <p className="text-sm leading-relaxed whitespace-pre-line">
      {parts.map((part, i) => {
        const marker = part.match(/^\[(\d+(?:,\s*\d+)*)\]$/)
        if (!marker) return <span key={i}>{part}</span>
        const ids = marker[1]!.split(',').map((n) => Number(n.trim()))
        return ids.map((id) => {
          const citation = bySource.get(id)
          if (!citation) return null
          return (
            <button
              key={`${i}-${id}`}
              onClick={() => onCite(citation)}
              title={`${citation.documentTitle}, page ${citation.pageStart}`}
              className="mx-0.5 inline-flex h-5 min-w-5 items-center justify-center rounded-md bg-accent-100 px-1 align-baseline text-[11px] font-semibold text-accent-700 hover:bg-accent-500 hover:text-white dark:bg-accent-500/20 dark:text-accent-100"
            >
              {id}
            </button>
          )
        })
      })}
    </p>
  )
}
