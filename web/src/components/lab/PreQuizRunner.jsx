import { Card, Progress, Radio, Input, Button, Space, Alert, Typography, Result } from 'antd'
import { useLabQuiz } from '../../hooks/useLabQuiz'

const { Title, Text } = Typography

/**
 * One-question-per-screen pre-lab quiz runner.
 * Props:
 *   labId
 *   onPassed(result)   — called after a passing submit
 *   onFailed(result)   — called after a failing submit (retry path)
 */
export default function PreQuizRunner({ labId, onPassed, onFailed }) {
  const q = useLabQuiz(labId)

  if (q.loading) return <Card loading />
  if (q.error) return <Alert type="error" message={q.error} showIcon />
  if (q.questions.length === 0) {
    return <Alert type="info" message="Lab này chưa có câu hỏi pre-lab." showIcon />
  }

  // Post-submit result screen
  if (q.result) {
    const passed = q.result.passed
    return (
      <Result
        status={passed ? 'success' : 'warning'}
        title={passed ? 'Bạn đã đạt pre-lab!' : 'Chưa đạt — thử lại'}
        subTitle={`Điểm: ${q.result.score_percent}% · Lần thử: ${q.result.attempt_number}`}
        extra={[
          passed ? (
            <Button key="go" type="primary" onClick={() => onPassed?.(q.result)}>
              Tiếp tục
            </Button>
          ) : (
            <Button key="retry" type="primary" onClick={() => window.location.reload()}>
              Làm lại
            </Button>
          ),
          !passed && (
            <Button key="back" onClick={() => onFailed?.(q.result)}>Quay về</Button>
          ),
        ]}
      />
    )
  }

  const question = q.current
  const percent = Math.round(((q.index + 1) / q.questions.length) * 100)
  const chosen = q.answers[question.id]

  return (
    <Card>
      <Progress percent={percent} showInfo={false} style={{ marginBottom: 16 }} />
      <Text type="secondary">Câu {q.index + 1} / {q.questions.length}</Text>
      <Title level={4} style={{ marginTop: 8 }}>{question.question_text}</Title>
      {question.hint && <Text type="secondary">💡 {question.hint}</Text>}

      <div style={{ marginTop: 24 }}>
        {question.question_type === 'multiple_choice' && question.options && (
          <Radio.Group
            value={chosen}
            onChange={(e) => q.setAnswer(question.id, e.target.value)}
          >
            <Space direction="vertical">
              {Object.entries(question.options).map(([key, text]) => (
                <Radio key={key} value={key}>
                  <Text strong>{key}.</Text> {text}
                </Radio>
              ))}
            </Space>
          </Radio.Group>
        )}
        {question.question_type === 'free_text' && (
          <Input.TextArea
            rows={4}
            value={chosen || ''}
            onChange={(e) => q.setAnswer(question.id, e.target.value)}
            placeholder="Nhập câu trả lời..."
          />
        )}
      </div>

      <Space style={{ marginTop: 24, width: '100%', justifyContent: 'space-between' }}>
        <Button onClick={q.prev} disabled={q.index === 0}>Trước</Button>
        {q.isLast ? (
          <Button
            type="primary"
            loading={q.submitting}
            disabled={!q.canNext}
            onClick={q.submit}
          >
            Nộp bài
          </Button>
        ) : (
          <Button type="primary" disabled={!q.canNext} onClick={q.next}>Tiếp</Button>
        )}
      </Space>
    </Card>
  )
}
