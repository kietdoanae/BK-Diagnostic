import { Component } from 'react'
import { Alert } from 'antd'

/**
 * Lightweight React Error Boundary. Wrap any subtree that may throw during
 * render to keep the rest of the page alive. Shows the error message + stack
 * so we can see what went wrong instead of seeing a blank screen.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null, info: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary] caught:', error, info)
    this.setState({ info })
  }

  render() {
    if (this.state.error) {
      const { error, info } = this.state
      const label = this.props.label || 'Một thành phần bị lỗi'
      return (
        <Alert
          type="error"
          showIcon
          message={label}
          description={
            <pre style={{
              whiteSpace: 'pre-wrap',
              fontSize: 12,
              margin: 0,
              maxHeight: 300,
              overflow: 'auto',
            }}>
              {String(error?.message || error)}
              {info?.componentStack ? `\n${info.componentStack}` : ''}
            </pre>
          }
          style={{ margin: 16 }}
        />
      )
    }
    return this.props.children
  }
}
