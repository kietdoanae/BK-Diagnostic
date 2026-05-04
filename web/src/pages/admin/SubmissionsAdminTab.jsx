import { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Tag,
  Space,
  Typography,
  Select,
  Button,
  message,
  Progress,
} from 'antd'
import { ReloadOutlined, FileZipOutlined, DownloadOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import JSZip from 'jszip'
import {
  listPostSubmissions,
  listLabs,
  listReports,
  downloadReportBlob,
} from '../../services/labApi'
import SubmissionDetail from '../../components/admin/SubmissionDetail'

const { Text } = Typography

function fmtTime(v) {
  if (!v) return '—'
  const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
  return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })
}

export default function SubmissionsAdminTab() {
  const { t } = useTranslation()
  const [labs, setLabs] = useState([])
  const [filterLab, setFilterLab] = useState(null)
  const [submissions, setSubmissions] = useState([])
  const [loading, setLoading] = useState(true)
  const [openSubmission, setOpenSubmission] = useState(null)
  const [zipping, setZipping] = useState(false)
  const [zipProgress, setZipProgress] = useState({ done: 0, total: 0 })

  async function reload() {
    setLoading(true)
    const [{ data: subs, error }, { data: ls }] = await Promise.all([
      listPostSubmissions({ labId: filterLab }),
      listLabs(),
    ])
    setLoading(false)
    if (error) {
      message.error(error.message)
      return
    }
    setLabs(ls || [])
    setSubmissions(subs || [])
  }

  useEffect(() => {
    reload()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterLab])

  /**
   * Download every PDF for the selected lab into a single ZIP. PDFs are
   * fetched as Blobs via supabase.storage.download (RLS allows staff to read
   * any file in lab-reports).
   *
   * Filename inside the ZIP uses pdf_storage_path's basename, which already
   * follows the spec's TR4021_LAB{NN}_{MSSV}_{Name}_{date}.pdf format.
   */
  async function handleBulkZip() {
    if (!filterLab) {
      message.warning(t('admin.submissionsTab.pickLabFirst'))
      return
    }
    const lab = labs.find((l) => l.id === filterLab)
    setZipping(true)
    setZipProgress({ done: 0, total: 0 })
    const { data: reports, error } = await listReports({ labId: filterLab })
    if (error) {
      setZipping(false)
      message.error(error.message)
      return
    }
    if (!reports || reports.length === 0) {
      setZipping(false)
      message.info(t('admin.submissionsTab.noPdfForLab'))
      return
    }
    setZipProgress({ done: 0, total: reports.length })

    const zip = new JSZip()
    let done = 0
    for (const r of reports) {
      const { data: blob, error: dlErr } = await downloadReportBlob(r.pdf_storage_path)
      if (!dlErr && blob) {
        const name = r.pdf_storage_path.split('/').pop()
        zip.file(name, blob)
      } else if (dlErr) {
        console.error('[bulkZip]', r.pdf_storage_path, dlErr.message)
      }
      done++
      setZipProgress({ done, total: reports.length })
    }
    const zipBlob = await zip.generateAsync({ type: 'blob' })
    const url = URL.createObjectURL(zipBlob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${lab.code}_submissions_${new Date().toISOString().slice(0, 10)}.zip`
    a.click()
    URL.revokeObjectURL(url)
    setZipping(false)
    message.success(t('admin.submissionsTab.zippedFmt', { count: done }))
  }

  const columns = [
    {
      title: t('admin.submission.mssvCol'),
      dataIndex: ['profile', 'mssv'],
      width: 110,
      render: (v) => <Text code>{v || '—'}</Text>,
    },
    {
      title: t('admin.submission.studentCol'),
      dataIndex: ['profile', 'full_name'],
      render: (v, r) => (
        <div>
          <Text>{v || r.profile?.username || '—'}</Text>
          <div style={{ fontSize: 11, color: '#9ca3af' }}>{r.profile?.email}</div>
        </div>
      ),
    },
    {
      title: t('admin.submission.labCol'),
      dataIndex: ['session', 'lab', 'code'],
      render: (v) => <Text code>{v}</Text>,
    },
    {
      title: t('admin.session.group'),
      dataIndex: ['session', 'group', 'name'],
    },
    {
      title: t('common.status'),
      dataIndex: 'is_draft',
      width: 110,
      render: (v) =>
        v ? <Tag color="warning">{t('admin.submission.draft')}</Tag> : <Tag color="success">{t('admin.submission.submitted')}</Tag>,
    },
    {
      title: t('common.updatedAt'),
      dataIndex: 'updated_at',
      width: 170,
      render: fmtTime,
    },
    {
      title: t('admin.submissionsTab.commentCol'),
      dataIndex: 'teacher_comment',
      render: (v) =>
        v ? (
          <Tag color="blue">{t('common.yes')}</Tag>
        ) : (
          <Text type="secondary" style={{ fontSize: 11 }}>
            —
          </Text>
        ),
    },
    {
      title: '',
      key: 'actions',
      align: 'right',
      render: (_, r) => (
        <Button size="small" icon={<DownloadOutlined />} onClick={() => setOpenSubmission(r)}>
          {t('common.open')}
        </Button>
      ),
    },
  ]

  return (
    <Card style={{ borderRadius: 16 }} styles={{ body: { padding: 0 } }}>
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <Select
          placeholder={t('admin.submission.filterLab')}
          value={filterLab || undefined}
          onChange={(v) => setFilterLab(v ?? null)}
          allowClear
          style={{ width: 280 }}
          options={labs.map((l) => ({
            value: l.id,
            label: `${l.code} — ${l.title}`,
          }))}
        />
        <Button icon={<ReloadOutlined />} onClick={reload}>
          {t('common.refresh')}
        </Button>
        <Button
          type="primary"
          icon={<FileZipOutlined />}
          loading={zipping}
          disabled={!filterLab}
          style={{ marginLeft: 'auto' }}
          onClick={handleBulkZip}
        >
          {t('admin.submissionsTab.btnZipAll')}
        </Button>
      </div>

      {zipping && zipProgress.total > 0 && (
        <div style={{ padding: '8px 16px' }}>
          <Progress
            percent={Math.round((zipProgress.done / zipProgress.total) * 100)}
            format={() => `${zipProgress.done}/${zipProgress.total}`}
          />
        </div>
      )}

      <Table
        rowKey="id"
        loading={loading}
        size="small"
        columns={columns}
        dataSource={submissions}
        pagination={{ pageSize: 20 }}
        scroll={{ x: 'max-content' }}
      />

      <SubmissionDetail
        open={!!openSubmission}
        submission={openSubmission}
        onClose={() => setOpenSubmission(null)}
        onChanged={reload}
      />
    </Card>
  )
}
