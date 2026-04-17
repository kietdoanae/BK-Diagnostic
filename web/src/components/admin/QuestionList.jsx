import { useState } from 'react'
import { Button, Card, Space, Tag, Popconfirm, Typography, message } from 'antd'
import {
  DragOutlined,
  EditOutlined,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import {
  SortableContext,
  arrayMove,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { reorderQuestions, deleteQuestion } from '../../services/labApi'
import QuestionForm from './QuestionForm'

const { Text } = Typography

const TYPE_COLOR = {
  multiple_choice: 'blue',
  free_text: 'green',
  image_upload: 'orange',
}

function SortableRow({ q, onEdit, onDelete }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: q.id })
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.6 : 1,
  }
  return (
    <div
      ref={setNodeRef}
      style={{
        ...style,
        display: 'flex',
        alignItems: 'center',
        padding: '8px 12px',
        borderBottom: '1px solid #f0f0f0',
        background: '#fff',
        gap: 12,
      }}
    >
      <span
        {...attributes}
        {...listeners}
        style={{ cursor: 'grab', color: '#9ca3af' }}
        aria-label="Kéo để sắp xếp"
      >
        <DragOutlined />
      </span>
      <Tag>#{q.question_order}</Tag>
      <Tag color={TYPE_COLOR[q.question_type] || 'default'}>{q.question_type}</Tag>
      <div style={{ flex: 1 }}>
        <Text>{q.question_text}</Text>
        <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
          {q.points} điểm
        </Text>
      </div>
      <Space>
        <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(q)}>
          Sửa
        </Button>
        <Popconfirm
          title="Xóa câu hỏi này?"
          okText="Xóa"
          cancelText="Hủy"
          onConfirm={() => onDelete(q)}
        >
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      </Space>
    </div>
  )
}

export default function QuestionList({ labId, phase, questions, onChanged }) {
  const [formOpen, setFormOpen] = useState(false)
  const [editingQ, setEditingQ] = useState(null)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }))

  const sorted = [...(questions || [])].sort(
    (a, b) => a.question_order - b.question_order
  )

  async function handleDragEnd(event) {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = sorted.findIndex((q) => q.id === active.id)
    const newIndex = sorted.findIndex((q) => q.id === over.id)
    const newOrder = arrayMove(sorted, oldIndex, newIndex)
    const { error } = await reorderQuestions(
      labId,
      phase,
      newOrder.map((q) => q.id)
    )
    if (error) {
      message.error(error.message)
      return
    }
    onChanged?.()
  }

  async function handleDelete(q) {
    const { error } = await deleteQuestion(q.id)
    if (error) message.error(error.message)
    else {
      message.success('Đã xóa')
      onChanged?.()
    }
  }

  const nextOrder =
    sorted.length === 0
      ? 1
      : Math.max(...sorted.map((q) => q.question_order)) + 1

  return (
    <Card
      size="small"
      title={`Questions — ${phase}`}
      styles={{ body: { padding: 0 } }}
      extra={
        <Button
          size="small"
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditingQ(null)
            setFormOpen(true)
          }}
        >
          Thêm câu hỏi
        </Button>
      }
    >
      {sorted.length === 0 ? (
        <div style={{ padding: 16, color: '#9ca3af' }}>Chưa có câu hỏi</div>
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={sorted.map((q) => q.id)}
            strategy={verticalListSortingStrategy}
          >
            {sorted.map((q) => (
              <SortableRow
                key={q.id}
                q={q}
                onEdit={(qq) => {
                  setEditingQ(qq)
                  setFormOpen(true)
                }}
                onDelete={handleDelete}
              />
            ))}
          </SortableContext>
        </DndContext>
      )}

      <QuestionForm
        open={formOpen}
        labId={labId}
        phase={phase}
        question={editingQ}
        nextOrder={nextOrder}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false)
          onChanged?.()
        }}
      />
    </Card>
  )
}
