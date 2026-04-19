import { useEffect, useState, useCallback } from 'react'
import { listQuestions, rpcSubmitPreQuiz } from '../services/labApi'

/**
 * Pre-lab quiz state manager. Loads questions in order, keeps answers in
 * local state, exposes paging + submit.
 *
 * Return shape:
 *   { loading, error, questions, index, answers, setAnswer,
 *     next, prev, canNext, isLast, submitting, result, submit }
 */
export function useLabQuiz(labId) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [questions, setQuestions] = useState([])
  const [index, setIndex] = useState(0)
  const [answers, setAnswers] = useState({}) // { [question_id]: 'A' | text }
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null) // { score_percent, passed, attempt_number }

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      const { data, error: err } = await listQuestions(labId, 'pre_lab')
      if (cancelled) return
      if (err) setError(err.message)
      else setQuestions(data || [])
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [labId])

  const setAnswer = useCallback((questionId, value) => {
    setAnswers((prev) => ({ ...prev, [questionId]: value }))
  }, [])

  const current = questions[index]
  const canNext = current ? answers[current.id] !== undefined && answers[current.id] !== '' : false
  const isLast = index === questions.length - 1

  const next = useCallback(() => {
    setIndex((i) => Math.min(i + 1, questions.length - 1))
  }, [questions.length])

  const prev = useCallback(() => {
    setIndex((i) => Math.max(i - 1, 0))
  }, [])

  const submit = useCallback(async () => {
    setSubmitting(true)
    setError(null)
    const { data, error: err } = await rpcSubmitPreQuiz(labId, answers)
    setSubmitting(false)
    if (err) {
      setError(err.message)
      return { ok: false, error: err.message }
    }
    setResult(data)
    return { ok: true, result: data }
  }, [labId, answers])

  return {
    loading,
    error,
    questions,
    index,
    current,
    answers,
    setAnswer,
    next,
    prev,
    canNext,
    isLast,
    submitting,
    result,
    submit,
  }
}
