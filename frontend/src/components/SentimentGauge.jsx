// Horizontal gauge from -1 to +1 with a marker for the current score.
function zoneColor(score) {
  if (score == null) return 'text-slate-400'
  if (score <= -0.3) return 'text-red-600'
  if (score < 0.3) return 'text-amber-500'
  return 'text-green-600'
}

function trendArrow(trend) {
  if (!trend) return ''
  if (trend.includes('POSITIVE')) return '↑'
  if (trend.includes('NEGATIVE') || trend.includes('DECLIN')) return '↓'
  return '→'
}

export default function SentimentGauge({ score, trend, topSignal }) {
  const hasScore = typeof score === 'number'
  // Map -1..+1 to 0..100%.
  const pct = hasScore ? ((score + 1) / 2) * 100 : 50

  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs uppercase tracking-wide text-slate-400">Sentiment (24h)</span>
        <span className={`text-sm font-semibold ${zoneColor(score)}`}>
          {hasScore ? score.toFixed(2) : 'n/a'} <span className="text-slate-400">{trendArrow(trend)}</span>
        </span>
      </div>
      <div className="relative h-3 rounded-full overflow-hidden bg-gradient-to-r from-red-400 via-amber-300 to-green-400">
        <div
          className="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-slate-900 border-2 border-white shadow"
          style={{ left: `calc(${pct}% - 6px)` }}
        />
      </div>
      {topSignal && <p className="text-xs text-slate-500 mt-2 leading-snug">{topSignal}</p>}
    </div>
  )
}
