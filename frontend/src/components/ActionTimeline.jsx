function actionLabel(a) {
  switch (a.actionType) {
    case 'MODE_TRANSITION':
      return `Mode switched ${a.fromValue} → ${a.toValue}`
    case 'PRICE_UPDATE':
      return a.guardrailBlocked
        ? `Price change to $${a.toValue} blocked`
        : `Price updated $${a.fromValue} → $${a.toValue}`
    case 'AD_BID_UPDATE':
      return `Ad bid updated $${a.fromValue} → $${a.toValue}`
    default:
      return 'No action taken'
  }
}

export default function ActionTimeline({ actions, productNames }) {
  if (!actions || actions.length === 0) {
    return <p className="text-sm text-slate-400">No actions logged yet.</p>
  }
  return (
    <ol className="relative border-l-2 border-slate-200 ml-3">
      {actions.map((a) => (
        <li key={a.id} className="mb-6 ml-6">
          <span className="absolute -left-3 flex items-center justify-center w-6 h-6 rounded-full bg-white border-2 border-slate-200 text-xs">
            {a.guardrailBlocked ? '🚫' : '✅'}
          </span>
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-xs text-slate-400">
              {a.createdAt ? new Date(a.createdAt).toLocaleString() : ''}
            </span>
            <span className="text-sm font-semibold text-slate-700">
              {productNames?.[a.productId] || a.productId}
            </span>
          </div>
          <p className="text-sm font-medium text-slate-800 mt-0.5">{actionLabel(a)}</p>
          {a.kimiExplanation && (
            <p className="text-sm text-slate-500 mt-1 leading-snug">{a.kimiExplanation}</p>
          )}
          {a.blockReason && (
            <p className="text-sm text-red-600 mt-1 leading-snug">{a.blockReason}</p>
          )}
          {a.daytonaJobId && (
            <span className="inline-block mt-2 px-2 py-0.5 rounded bg-slate-100 text-slate-500 text-xs">
              Executed via Daytona sandbox · {a.daytonaJobId}
            </span>
          )}
        </li>
      ))}
    </ol>
  )
}
