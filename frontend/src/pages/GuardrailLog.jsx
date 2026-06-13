import { useQuery } from '@tanstack/react-query'
import api from '../api/client.js'

function useProductNames() {
  const { data } = useQuery({
    queryKey: ['products'],
    queryFn: () => api.get('/api/products').then((r) => r.data),
  })
  const names = {}
  ;(data || []).forEach((p) => {
    names[p.id] = p.name
  })
  return names
}

function actionAttempted(a) {
  switch (a.actionType) {
    case 'MODE_TRANSITION':
      return `Mode ${a.fromValue} → ${a.toValue}`
    case 'PRICE_UPDATE':
      return 'Price update'
    case 'AD_BID_UPDATE':
      return 'Ad bid update'
    default:
      return 'No action'
  }
}

export default function GuardrailLog() {
  const productNames = useProductNames()

  const { data: actions } = useQuery({
    queryKey: ['actions'],
    queryFn: () => api.get('/api/actions').then((r) => r.data),
  })

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900 mb-1">Guardrail Log</h1>
      <p className="text-sm text-slate-500 mb-6">
        Every action the agent took or tried to take. Blocked rows are where your hard rules
        overrode the AI — the AI can never break them.
      </p>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-400">
            <tr>
              <th className="px-4 py-3 font-medium">Timestamp</th>
              <th className="px-4 py-3 font-medium">Product</th>
              <th className="px-4 py-3 font-medium">Action attempted</th>
              <th className="px-4 py-3 font-medium">Proposed</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Block reason</th>
            </tr>
          </thead>
          <tbody>
            {(actions || []).map((a) => (
              <tr
                key={a.id}
                className={a.guardrailBlocked ? 'bg-red-50' : 'bg-green-50/40'}
              >
                <td className="px-4 py-3 text-slate-500 whitespace-nowrap">
                  {a.createdAt ? new Date(a.createdAt).toLocaleString() : ''}
                </td>
                <td className="px-4 py-3 font-medium text-slate-700">
                  {productNames[a.productId] || a.productId}
                </td>
                <td className="px-4 py-3">{actionAttempted(a)}</td>
                <td className="px-4 py-3">{a.toValue ?? '—'}</td>
                <td className="px-4 py-3">
                  {a.guardrailBlocked ? (
                    <span className="px-2 py-0.5 rounded bg-red-600 text-white text-xs font-medium">🚫 Blocked</span>
                  ) : (
                    <span className="px-2 py-0.5 rounded bg-green-600 text-white text-xs font-medium">✅ Executed</span>
                  )}
                </td>
                <td className="px-4 py-3 text-red-600 max-w-md">{a.blockReason || ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {(!actions || actions.length === 0) && (
          <p className="px-4 py-6 text-slate-400 text-sm">No actions logged yet.</p>
        )}
      </div>
    </div>
  )
}
