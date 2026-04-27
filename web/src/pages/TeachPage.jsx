import { Tabs, Empty, Card } from 'antd'
import { ReadOutlined, FileTextOutlined, TeamOutlined } from '@ant-design/icons'
import AppLayout from '../components/AppLayout'

function LabManagementTab() {
  return (
    <Card>
      <Empty
        image={<ReadOutlined style={{ fontSize: 48, color: '#1565C0' }} />}
        description={
          <>
            <p style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>
              Quản lý nội dung Lab
            </p>
            <p style={{ color: '#6B7280' }}>
              Danh sách lab và editor sẽ được tích hợp ở phase tiếp theo.
            </p>
          </>
        }
      />
    </Card>
  )
}

function StudentReportsTab() {
  return (
    <Card>
      <Empty
        image={<FileTextOutlined style={{ fontSize: 48, color: '#1565C0' }} />}
        description={
          <>
            <p style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>
              Báo cáo của sinh viên
            </p>
            <p style={{ color: '#6B7280' }}>Coming soon.</p>
          </>
        }
      />
    </Card>
  )
}

function GroupsTab() {
  return (
    <Card>
      <Empty
        image={<TeamOutlined style={{ fontSize: 48, color: '#1565C0' }} />}
        description={
          <>
            <p style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>
              Quản lý nhóm Lab
            </p>
            <p style={{ color: '#6B7280' }}>Coming soon.</p>
          </>
        }
      />
    </Card>
  )
}

export default function TeachPage() {
  const items = [
    { key: 'labs',    label: 'Quản lý Lab',         children: <LabManagementTab /> },
    { key: 'reports', label: 'Báo cáo sinh viên',   children: <StudentReportsTab /> },
    { key: 'groups',  label: 'Quản lý nhóm',        children: <GroupsTab /> },
  ]

  return (
    <AppLayout>
      <div style={{ maxWidth: 1200, margin: '0 auto', padding: 24 }}>
        <h1 style={{ fontSize: 28, fontWeight: 700, marginBottom: 8 }}>
          Phần giảng dạy
        </h1>
        <p style={{ color: '#6B7280', marginBottom: 24 }}>
          Tạo và quản lý nội dung Lab, xem báo cáo sinh viên, quản lý nhóm thực hành.
        </p>
        <Tabs defaultActiveKey="labs" items={items} />
      </div>
    </AppLayout>
  )
}
