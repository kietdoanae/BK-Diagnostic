import React from 'react'
import ReactDOM from 'react-dom/client'
import { AuthProvider } from './context/AuthContext'
import App from './App'
import './index.css'
import './styles/design-tokens.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </React.StrictMode>
)
