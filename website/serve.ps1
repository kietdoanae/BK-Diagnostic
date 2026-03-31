$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add('http://localhost:9090/')
$listener.Start()
Write-Host 'Listening on http://localhost:9090'

$root = Join-Path $PSScriptRoot ''

while ($listener.IsListening) {
    $context  = $listener.GetContext()
    $path     = $context.Request.Url.LocalPath
    if ($path -eq '/' -or $path -eq '') { $path = '/index.html' }

    $filePath = Join-Path $root ($path.TrimStart('/'))

    if (Test-Path $filePath -PathType Leaf) {
        $bytes = [System.IO.File]::ReadAllBytes($filePath)
        $ext   = [System.IO.Path]::GetExtension($filePath).ToLower()
        $mime  = switch ($ext) {
            '.html' { 'text/html; charset=utf-8' }
            '.css'  { 'text/css' }
            '.js'   { 'application/javascript' }
            '.png'  { 'image/png' }
            '.jpg'  { 'image/jpeg' }
            '.ico'  { 'image/x-icon' }
            default { 'text/plain' }
        }
        $context.Response.ContentType   = $mime
        $context.Response.ContentLength64 = $bytes.Length
        $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    } else {
        $context.Response.StatusCode = 404
        $body = [System.Text.Encoding]::UTF8.GetBytes('404 Not Found')
        $context.Response.OutputStream.Write($body, 0, $body.Length)
    }
    $context.Response.Close()
}
