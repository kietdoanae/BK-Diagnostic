$l = New-Object Net.HttpListener
$l.Prefixes.Add('http://localhost:9090/')
$l.Start()
Write-Host 'Listening on http://localhost:9090'
$r = 'C:/Users/KIET/AndroidStudioProjects/BKDiagnostic/app/src/main/assets'
while ($l.IsListening) {
    $c = $l.GetContext()
    $p = $c.Request.Url.LocalPath
    if ($p -eq '/' -or $p -eq '') { $p = '/wiring_diagram.html' }
    $f = $r + $p
    if (Test-Path $f -PathType Leaf) {
        $b = [IO.File]::ReadAllBytes($f)
        $e = [IO.Path]::GetExtension($f).ToLower()
        $m = if ($e -eq '.html') { 'text/html; charset=utf-8' } elseif ($e -eq '.css') { 'text/css' } elseif ($e -eq '.js') { 'application/javascript' } else { 'text/plain' }
        $c.Response.ContentType = $m
        $c.Response.ContentLength64 = $b.Length
        $c.Response.OutputStream.Write($b, 0, $b.Length)
    } else {
        $c.Response.StatusCode = 404
    }
    $c.Response.Close()
}
