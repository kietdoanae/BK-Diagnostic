import { useState } from 'react'
import { Button, Card, List, Space, Tag, Popconfirm, Typography, message } from 'antd'
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
import { reorderSteps, deleteStep } from '../../services/labApi'
import StepForm from './StepForm'

const { Text } = Typography

const TYPE_COLOR = {
  raw_frames: 'geekblue',
  active_test: 'purple',
  screenshot: 'cyan',
  none: 'default',
}

function SortableRow({ step, onEdit, onDelete }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: step.id })
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
      <Tag>#{step.step_order}</Tag>
      <Tag color={TYPE_COLOR[step.evidence_type] || 'default'}>
        {step.evidence_type}
      </Tag>
      <div style={{ flex: 1 }}>
        <Text strong>{step.title}</Text>
        <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
          required = {step.required_count ?? 0}
        </Text>
      </div>
      <Space>
        <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(step)}>
          Sửa
        </Button>
        <Popconfirm
          title="Xóa step này?"
          okText="Xóa"
          cancelText="Hủy"
          onConfirm={() => onDelete(step)}
        >
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      </Space>
    </div>
  )
}

export default function StepList({ labId, steps, onChanged }) {
  const [formOpen, setFormOpen] = useState(false)
  const [editingStep, setEditingStep] = useState(null)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }))

  const sorted = [...(steps || [])].sort((a, b) => a.step_order - b.step_order)

  async function handleDragEnd(event) {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = sorted.findIndex((s) => s.id === active.id)
    const newIndex = sorted.findIndex((s) => s.id === over.id)
    const newOrder = arrayMove(sorted, oldIndex, newIndex)
    const { error } = await reorderSteps(
      labId,
      newOrder.map((s) => s.id)
    )
    if (error) {
      message.error(error.message)
      return
    }
    onChanged?.()
  }

  async function handleDelete(step) {
    const { error } = await deleteStep(step.id)
    if (error) message.error(error.message)
    else {
      message.success('Đã xóa')
      onChanged?.()
    }
  }

  const nextOrder =
    sorted.length === 0 ? 1 : Math.max(...sorted.map((s) => s.step_order)) + 1

  return (
    <Card
      size="small"
      title="Steps"
      styles={{ body: { padding: 0 } }}
      extra={
        <Button
          size="small"
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditingStep(null)
            setFormOpen(true)
          }}
        >
          Thêm step
        </Button>
      }
    >
      {sorted.length === 0 ? (
        <List locale={{ emptyText: 'Chưa có step' }} dataSource={[]} renderItem={() => null} />
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={sorted.map((s) => s.id)}
            strategy={verticalListSortingStrategy}
          >
            {sorted.map((s) => (
              <SortableRow
                key={s.id}
                step={s}
                onEdit={(st) => {
                  setEditingStep(st)
                  setFormOpen(true)
                }}
                onDelete={handleDelete}
              />
            ))}
          </SortableContext>
        </DndContext>
      )}

      <StepForm
        open={formOpen}
        labId={labId}
        step={editingStep}
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
