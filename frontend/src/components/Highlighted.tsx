// The API marks keyword matches with private-use code points so no HTML is ever injected.
const START = ''
const END = ''

export function Highlighted({ text }: { text: string }) {
  const parts: { text: string; match: boolean }[] = []
  let rest = text
  while (rest.length) {
    const s = rest.indexOf(START)
    if (s === -1) {
      parts.push({ text: rest, match: false })
      break
    }
    const e = rest.indexOf(END, s)
    if (s > 0) parts.push({ text: rest.slice(0, s), match: false })
    parts.push({ text: rest.slice(s + 1, e === -1 ? undefined : e), match: true })
    rest = e === -1 ? '' : rest.slice(e + 1)
  }
  return (
    <>
      {parts.map((p, i) =>
        p.match ? (
          <mark key={i} className="rounded bg-amber-200/70 px-0.5 text-inherit dark:bg-amber-500/30">
            {p.text}
          </mark>
        ) : (
          <span key={i}>{p.text}</span>
        ),
      )}
    </>
  )
}
