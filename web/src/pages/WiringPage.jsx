import AppLayout from '../components/AppLayout'

export default function WiringPage() {
  return (
    <AppLayout>
      <div style={{
        height: 'calc(100vh - 112px)',
        borderRadius: 16,
        overflow: 'hidden',
        border: '1px solid #e8edf5',
        background: '#fff',
        boxShadow: '0 2px 12px rgba(0,0,0,0.06)',
      }}>
        <iframe
          src="/wiring_diagram.html"
          style={{ width: '100%', height: '100%', border: 'none' }}
          title="Wiring Diagram"
        />
      </div>
    </AppLayout>
  )
}
