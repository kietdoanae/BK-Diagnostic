import { useState } from 'react'
import { Collapse, List, Tag, Typography, Button, Empty, message } from 'antd'
import { EyeOutlined } from '@ant-design/icons'
import { getLabImageSignedUrl } from '../../services/labApi'

const { Text } = Typography

/**
 * Collapsible panel listing evidence rows filtered to a subset of steps.
 * Used on the post-lab page so students can re-examine captured CAN frames
 * / active-test commands / screenshots while writing analysis.
 *
 * Props:
 *   evidence — array of lab_evidence rows (already loaded by parent)
 *   steps    — lab_steps rows (for labeling)
 *   stepIds  — optional filter — only show evidence for these steps
 */
export default function EvidenceInlineViewer({ evidence, steps, stepIds }) {
  const filtered = stepIds
    ? evidence.filter((e) => stepIds.includes(e.step_id))
    : evidence

  if (filtered.length === 0) {
    return <Empty description="Chưa có bằng chứng để xem" />
  }

  const byStep = new Map()
  for (const e of filtered) {
    const list = byStep.get(e.step_id) || []
    list.push(e)
    byStep.set(e.step_id, list)
  }

  const items = Array.from(byStep.entries()).map(([stepId, rows]) => {
    const step = steps.find((s) => s.id === stepId)
    return {
      key: stepId,
      label: (
        <span>
          <Tag color="blue">Bước {step?.step_order ?? '?'}</Tag>
          {step?.title} · {rows.length} bằng chứng
        </span>
      ),
      children: <EvidenceRowList rows={rows} />,
    }
  })

  return <Collapse items={items} />
}

function EvidenceRowList({ rows }) {
  const [signedUrls, setSignedUrls] = useState({})

  async function openImage(path) {
    if (signedUrls[path]) {
      window.open(signedUrls[path], '_blank')
      return
    }
    const { data, error } = await getLabImageSignedUrl(path, 120)
    if (error) {
      message.error(error.message)
      return
    }
    setSignedUrls((prev) => ({ ...prev, [path]: data.signedUrl }))
    window.open(data.signedUrl, '_blank')
  }

  return (
    <List
      size="small"
      dataSource={rows}
      renderItem={(r) => {
        const ts = new Date(r.client_timestamp_ms).toLocaleTimeString('vi-VN')
        if (r.evidence_type === 'raw_frame') {
          const frames = r.payload?.frames?.length ?? 0
          return (
            <List.Item>
              <Text type="secondary">{ts}</Text> · <Tag>raw_frame</Tag>
              <Text> {frames} frame{frames !== 1 ? 's' : ''} trong batch</Text>
            </List.Item>
          )
        }
        if (r.evidence_type === 'active_test') {
          return (
            <List.Item>
              <Text type="secondary">{ts}</Text> · <Tag color="orange">active_test</Tag>
              <Text code>{r.payload?.command || JSON.stringify(r.payload)}</Text>
            </List.Item>
          )
        }
        if (r.evidence_type === 'screenshot') {
          const path = r.payload?.image_path
          return (
            <List.Item
              actions={[
                <Button
                  key="view"
                  size="small"
                  icon={<EyeOutlined />}
                  onClick={() => openImage(path)}
                  disabled={!path}
                >
                  Xem
                </Button>,
              ]}
            >
              <Text type="secondary">{ts}</Text> · <Tag color="purple">screenshot</Tag>
              <Text>{r.payload?.original_name || path}</Text>
            </List.Item>
          )
        }
        return (
          <List.Item>
            <Text type="secondary">{ts}</Text> · <Tag>{r.evidence_type}</Tag>
          </List.Item>
        )
      }}
    />
  )
}
