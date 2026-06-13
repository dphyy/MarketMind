const STYLES = {
  HARVEST: 'bg-green-600 text-white',
  HOLD: 'bg-amber-400 text-slate-900',
  BUNKER: 'bg-red-600 text-white',
  GHOST: 'bg-slate-500 text-white',
}

export default function ModeBadge({ mode }) {
  const cls = STYLES[mode] || 'bg-slate-300 text-slate-800'
  return (
    <span className={`inline-block px-3 py-1 rounded-full text-xs font-bold tracking-wide ${cls}`}>
      {mode}
    </span>
  )
}
