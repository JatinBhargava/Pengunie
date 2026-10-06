import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnswerText } from './AnswerText'
import type { Citation } from '../api/types'

const citation: Citation = {
  sourceId: 1,
  chunkId: 'c1',
  documentId: 'd1',
  documentTitle: 'Policy',
  pageStart: 3,
  pageEnd: 3,
  quote: 'renews on 10 December',
}

describe('AnswerText', () => {
  it('renders validated citations as chips and drops unvalidated markers', async () => {
    const onCite = vi.fn()
    render(<AnswerText text="It renews in December [1]. Premium is high [2]." citations={[citation]} onCite={onCite} />)

    const chips = screen.getAllByRole('button')
    expect(chips).toHaveLength(1)
    expect(chips[0]).toHaveTextContent('1')
    expect(screen.queryByText('[2]')).not.toBeInTheDocument()

    await userEvent.click(chips[0]!)
    expect(onCite).toHaveBeenCalledWith(citation)
  })

  it('supports grouped markers', () => {
    render(<AnswerText text="Both [1, 3]." citations={[citation, { ...citation, sourceId: 3 }]} onCite={() => {}} />)
    expect(screen.getAllByRole('button')).toHaveLength(2)
  })
})
