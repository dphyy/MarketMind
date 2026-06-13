const LEVEL_STYLES = {
  CRITICALLY_LOW: 'bg-red-100 text-red-700',
  OUT_OF_STOCK: 'bg-red-200 text-red-800',
  LOW: 'bg-amber-100 text-amber-700',
  NORMAL: 'bg-slate-100 text-slate-600',
}

export default function CompetitorTable({ competitors }) {
  if (!competitors || competitors.length === 0) {
    return <p className="text-xs text-slate-400">No competitor data yet.</p>
  }
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
          <th className="py-1 font-medium">Competitor</th>
          <th className="py-1 font-medium">Price</th>
          <th className="py-1 font-medium">Stock</th>
          <th className="py-1 font-medium">Signals</th>
        </tr>
      </thead>
      <tbody>
        {competitors.map((c) => (
          <tr key={c.id ?? c.competitorName} className="border-t border-slate-100">
            <td className="py-1.5 font-medium text-slate-700">{c.competitorName}</td>
            <td className="py-1.5">${Number(c.price).toFixed(2)}</td>
            <td className="py-1.5">
              <span className={`px-2 py-0.5 rounded text-xs font-medium ${LEVEL_STYLES[c.stockLevel] || 'bg-slate-100 text-slate-600'}`}>
                {c.stockLevel}
              </span>
            </td>
            <td className="py-1.5">
              <div className="flex flex-wrap gap-1">
                {(c.visualSignals || []).map((s) => (
                  <span key={s} className="px-2 py-0.5 rounded bg-indigo-100 text-indigo-700 text-xs">
                    {s}
                  </span>
                ))}
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
