import http.server, os, sys
os.chdir(r'C:/Users/KIET/AndroidStudioProjects/BKDiagnostic/app/src/main/assets')
handler = http.server.SimpleHTTPRequestHandler
httpd = http.server.HTTPServer(('localhost', 9091), handler)
print('Serving on http://localhost:9091', flush=True)
httpd.serve_forever()
