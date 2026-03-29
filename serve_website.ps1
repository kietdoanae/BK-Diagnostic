$port = if ($env:PORT) { $env:PORT } else { '8080' }
$l = New-Object Net.HttpListener
$l.Prefixes.Add("http://localhost:$port/")
$l.Start()
Write-Host "Listening on http://localhost:$port"
$r = 'C:/Users/KIET/AndroidStudioProjects/BKDiagnostic/website'
while ($l.IsListening) {
    $c = $l.GetContext()
    $p = $c.Request.Url.LocalPath
    if ($p -eq '/' -or $p -eq '') { $p = '/dashboard.html' }
    $f = $r + $p
    if (Test-Path $f -PathType Leaf) {
        $b = [IO.File]::ReadAllBytes($f)
        $e = [IO.Path]::GetExtension($f).ToLower()
        $m = if ($e -eq '.html') { 'text/html; charset=utf-8' } elseif ($e -eq '.css') { 'text/css' } elseif ($e -eq '.js') { 'application/javascript' } elseif ($e -eq '.png') { 'image/png' } elseif ($e -eq '.svg') { 'image/svg+xml' } else { 'text/plain' }
        $c.Response.ContentType = $m
        $c.Response.ContentLength64 = $b.Length
        $c.Response.OutputStream.Write($b, 0, $b.Length)
    } else {
        $c.Response.StatusCode = 404
    }
    $c.Response.Close()
}
