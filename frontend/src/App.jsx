import { NavLink, Route, Routes } from 'react-router-dom'
import Dashboard from './pages/Dashboard.jsx'
import MorningBrief from './pages/MorningBrief.jsx'
import GuardrailLog from './pages/GuardrailLog.jsx'

function NavTab({ to, children }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        `px-4 py-2 rounded-lg text-sm font-medium transition ${
          isActive ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-200'
        }`
      }
    >
      {children}
    </NavLink>
  )
}

export default function App() {
  return (
    <div className="min-h-screen">
      <header className="bg-white border-b border-slate-200 sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-6 py-3 flex items-center gap-6">
          <div className="flex items-center gap-2">
            <span className="text-xl">🧠</span>
            <span className="text-lg font-bold tracking-tight">MarketMind</span>
          </div>
          <nav className="flex gap-1">
            <NavTab to="/">Dashboard</NavTab>
            <NavTab to="/brief">Morning Brief</NavTab>
            <NavTab to="/guardrails">Guardrail Log</NavTab>
          </nav>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-6 py-6">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/brief" element={<MorningBrief />} />
          <Route path="/guardrails" element={<GuardrailLog />} />
        </Routes>
      </main>
    </div>
  )
}
