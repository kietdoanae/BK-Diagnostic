import { useState } from 'react'
import {
  Modal,
  Upload,
  Button,
  Table,
  Tag,
  Typography,
  Alert,
  Select,
  Space,
  message,
} from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { bulkImportGroups } from '../../services/labApi'

const { Text, Paragraph } = Typography

/**
 * CSV format (one group per row):
 *   group_name,semester,leader_mssv,member_mssvs
 *   "Nhóm 1","HK2-2025-2026","2210001","2210002;2210003;2210004"
 *
 * - Header row required.
 * - member_mssvs uses ';' to separate multiple MSSVs (commas are reserved
 *   for CSV). The leader's MSSV does NOT need to appear in member_mssvs;
 *   the importer adds them automatically as 'leader'.
 */
function parseCsv(text) {
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0)
  if (lines.length < 2) return { rows: [], error: 'CSV phải có ít nhất header + 1 dòng dữ liệu' }
  const header = splitCsvLine(lines[0]).map((h) => h.trim().toLowerCase())
  const need = ['group_name', 'semester', 'leader_mssv', 'member_mssvs']
  for (const k of need) {
    if (!header.includes(k)) return { rows: [], error: `Thiếu cột: ${k}` }
  }
  const idx = (k) => header.indexOf(k)
  const rows = []
  for (let i = 1; i < lines.length; i++) {
    const cols = splitCsvLine(lines[i])
    rows.push({
      groupName: cols[idx('group_name')]?.trim(),
      semester: cols[idx('semester')]?.trim(),
      leaderMssv: cols[idx('leader_mssv')]?.trim(),
      memberMssvs: (cols[idx('member_mssvs')] || '')
        .split(';')
        .map((s) => s.trim())
        .filter(Boolean),
    })
  }
  return { rows, error: null }
}

// Minimal CSV splitter that respects double-quoted fields.
function splitCsvLine(line) {
  const out = []
  let cur = ''
  let inQ = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (inQ) {
      if (c === '"' && line[i + 1] === '"') {
        cur += '"'
        i++
      } else if (c === '"') {
        inQ = false
      } else {
        cur += c
      }
    } else {
      if (c === ',') {
        out.push(cur)
        cur = ''
      } else if (c === '"') {
        inQ = true
      } else {
        cur += c
      }
    }
  }
  out.push(cur)
  return out
}

export default function GroupBulkImport({ open, labs, onClose, onImported }) {
  const [labId, setLabId] = useState(null)
  const [rows, setRows] = useState([])
  const [parseError, setParseError] = useState(null)
  const [results, setResults] = useState(null)
  const [importing, setImporting] = useState(false)

  function handleFile(file) {
    const reader = new FileReader()
    reader.onload = (e) => {
      const { rows: r, error } = parseCsv(e.target.result)
      if (error) {
        setParseError(error)
        setRows([])
      } else {
        setParseError(null)
        setRows(r)
      }
      setResults(null)
    }
    reader.readAsText(file, 'utf-8')
    return false // prevent antd from auto-uploading
  }

  async function handleImport() {
    if (!labId) {
      message.warning('Chọn lab trước')
      return
    }
    if (rows.length === 0) {
      message.warning('Chưa có dữ liệu')
      return
    }
    setImporting(true)
    const { results: r, error } = await bulkImportGroups(labId, rows)
    setImporting(false)
    if (error) {
      message.error(error.message)
      return
    }
    setResults(r)
    const okCount = r.filter((x) => x.ok).length
    message[okCount === r.length ? 'success' : 'warning'](
      `${okCount}/${r.length} nhóm import thành công`
    )
    onImported?.()
  }

  return (
    <Modal
      open={open}
      title="Bulk import nhóm từ CSV"
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          Đóng
        </Button>,
        <Button
          key="import"
          type="primary"
          loading={importing}
          disabled={rows.length === 0 || !labId}
          onClick={handleImport}
        >
          Import {rows.length} nhóm
        </Button>,
      ]}
      width={820}
      destroyOnClose
    >
      <Paragraph type="secondary" style={{ fontSize: 12 }}>
        Định dạng CSV (UTF-8, có header):{' '}
        <Text code>group_name,semester,leader_mssv,member_mssvs</Text>. MSSV
        thành viên cách nhau bằng dấu chấm phẩy (<Text code>;</Text>). Leader
        sẽ được tự động thêm — không cần liệt kê lại trong{' '}
        <Text code>member_mssvs</Text>.
      </Paragraph>

      <Space style={{ display: 'flex', marginBottom: 12 }}>
        <Select
          placeholder="Chọn lab để import vào"
          style={{ width: 360 }}
          value={labId}
          onChange={setLabId}
          options={(labs || []).map((l) => ({
            value: l.id,
            label: `${l.code} — ${l.title}`,
          }))}
        />
        <Upload accept=".csv,text/csv" beforeUpload={handleFile} showUploadList={false}>
          <Button icon={<UploadOutlined />}>Chọn file CSV</Button>
        </Upload>
      </Space>

      {parseError && <Alert type="error" message={parseError} style={{ marginBottom: 12 }} />}

      {rows.length > 0 && !results && (
        <Table
          size="small"
          rowKey={(_, i) => i}
          pagination={{ pageSize: 8 }}
          columns={[
            { title: 'Tên nhóm', dataIndex: 'groupName' },
            { title: 'Học kỳ', dataIndex: 'semester' },
            { title: 'Leader MSSV', dataIndex: 'leaderMssv', render: (v) => <Text code>{v}</Text> },
            {
              title: 'Member MSSVs',
              dataIndex: 'memberMssvs',
              render: (arr) => (arr || []).map((m) => <Tag key={m}>{m}</Tag>),
            },
          ]}
          dataSource={rows}
        />
      )}

      {results && (
        <Table
          size="small"
          rowKey={(_, i) => i}
          pagination={false}
          columns={[
            {
              title: '',
              dataIndex: 'ok',
              width: 60,
              render: (v) =>
                v ? <Tag color="success">OK</Tag> : <Tag color="error">FAIL</Tag>,
            },
            { title: 'Tên nhóm', dataIndex: 'groupName' },
            {
              title: 'Lỗi',
              dataIndex: 'error',
              render: (v) => v && <Text type="danger">{v}</Text>,
            },
          ]}
          dataSource={results}
        />
      )}
    </Modal>
  )
}
