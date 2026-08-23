import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Starfield from './components/Starfield';
import AppLayout from './components/AppLayout';
import IntroPage from './intro/IntroPage';
import LoginPage from './pages/LoginPage';
import ConsentPage from './pages/ConsentPage';
import DashboardPage from './pages/DashboardPage';
import ReportPage from './pages/ReportPage';
import ChatPage from './pages/ChatPage';
import GuidePage from './pages/GuidePage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Starfield />
        <Routes>
          <Route path="/intro" element={<IntroPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/consent" element={<ConsentPage />} />

          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/report" element={<ReportPage />} />
            <Route path="/report/:id" element={<ReportPage />} />
            <Route path="/chat" element={<ChatPage />} />
            <Route path="/chat/:id" element={<ChatPage />} />
            <Route path="/guide" element={<GuidePage />} />
          </Route>

          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
