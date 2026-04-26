import pipelineSvg from '../../../assets/svg/pipeline-diagram.svg'

export default function PipelineDiagram() {
  return (
    <figure style={{ margin: '24px 0 0', textAlign: 'center' }}>
      <img
        src={pipelineSvg}
        alt="Pipeline dữ liệu từ xe đến cloud: Xe → MCP2515 → STM32 → CP2102 → App → Web"
        style={{ maxWidth: '100%', height: 'auto' }}
      />
      <figcaption style={{
        marginTop: 12,
        fontSize: 12,
        fontStyle: 'italic',
        color: 'var(--gold-500)',
        fontWeight: 500,
      }}>
        Hình 3 — Pipeline dữ liệu từ xe đến cloud.
      </figcaption>
    </figure>
  )
}
