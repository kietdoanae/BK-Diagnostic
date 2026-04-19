import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import ProtectedRoute from './components/ProtectedRoute'
import AdminRoute from './components/AdminRoute'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import DashboardPage from './pages/DashboardPage'
import AdminPage from './pages/AdminPage'
import WiringPage from './pages/WiringPage'
import LabsListPage from './pages/LabsListPage'
import LabOverviewPage from './pages/LabOverviewPage'
import LabSessionPage from './pages/LabSessionPage'
import LabPostLabPage from './pages/LabPostLabPage'
import LabReportPage from './pages/LabReportPage'
import MyReportsPage from './pages/MyReportsPage'

const theme = {
  token: {
    colorPrimary: '#1565C0',
    colorLink: '#1565C0',
    borderRadius: 8,
    fontFamily: "'Inter', sans-serif",
  },
}

export default function App() {
  return (
    <ConfigProvider theme={theme}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
          <Route path="/wiring" element={<ProtectedRoute><WiringPage /></ProtectedRoute>} />
          <Route path="/labs" element={<ProtectedRoute><LabsListPage /></ProtectedRoute>} />
          <Route path="/labs/:labId" element={<ProtectedRoute><LabOverviewPage /></ProtectedRoute>} />
          <Route path="/labs/:labId/session/:sid" element={<ProtectedRoute><LabSessionPage /></ProtectedRoute>} />
          <Route path="/labs/:labId/session/:sid/post" element={<ProtectedRoute><LabPostLabPage /></ProtectedRoute>} />
          <Route path="/labs/:labId/session/:sid/report" element={<ProtectedRoute><LabReportPage /></ProtectedRoute>} />
          <Route path="/my-reports" element={<ProtectedRoute><MyReportsPage /></ProtectedRoute>} />
          <Route path="/admin" element={<AdminRoute><AdminPage /></AdminRoute>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  )
}
