import MDEditor from '@uiw/react-md-editor'

/**
 * Thin wrapper around @uiw/react-md-editor that:
 *  - forces light color mode (admin UI is light-only — see AppLayout)
 *  - exposes a simple value/onChange API consistent with antd Form fields
 *  - hides the preview pane by default (toggleable via `showPreview`)
 */
export default function MarkdownEditor({
  value,
  onChange,
  height = 240,
  showPreview = false,
  placeholder,
}) {
  return (
    <div data-color-mode="light">
      <MDEditor
        value={value || ''}
        onChange={(v) => onChange?.(v ?? '')}
        height={height}
        preview={showPreview ? 'live' : 'edit'}
        textareaProps={{ placeholder }}
      />
    </div>
  )
}
