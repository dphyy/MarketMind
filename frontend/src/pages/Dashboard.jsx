import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '../api/client.js'
import ModeBadge from '../components/ModeBadge.jsx'
import SentimentGauge from '../components/SentimentGauge.jsx'
import CompetitorTable from '../components/CompetitorTable.jsx'
import RunAgentButton from '../components/RunAgentButton.jsx'

function PriceBar({ price, floor, ceiling }) {
  const p = Number(price)
  const lo = Number(floor)
  const hi = Number(ceiling)
  const pct = hi > lo ? Math.min(100, Math.max(0, ((p - lo) / (hi - lo)) * 100)) : 50
  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs uppercase tracking-wide text-slate-400">Your price</span>
        <span className="text-lg font-bold text-slate-900">${p.toFixed(2)}</span>
      </div>
      <div className="relative h-2 rounded-full bg-slate-200">
        <div className="absolute top-1/2 -translate-y-1/2 w-2.5 h-2.5 rounded-full bg-indigo-600" style={{ left: `calc(${pct}% - 5px)` }} />
      </div>
      <div className="flex justify-between text-xs text-slate-400 mt-1">
        <span>floor ${lo.toFixed(2)}</span>
        <span>ceiling ${hi.toFixed(2)}</span>
      </div>
    </div>
  )
}

function StockLine({ stock, reserve }) {
  const approaching = stock <= reserve * 1.2
  return (
    <div className="flex items-center justify-between">
      <span className="text-xs uppercase tracking-wide text-slate-400">Stock</span>
      <span className={`text-sm font-semibold ${approaching ? 'text-red-600' : 'text-slate-700'}`}>
        {stock} units {approaching && <span className="ml-1">⚠ near reserve ({reserve})</span>}
      </span>
    </div>
  )
}

function ProductCard({ overview }) {
  const { product, sentiment, competitors } = overview
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
      <div className="flex items-start justify-between gap-3 mb-4">
        <div>
          <h3 className="font-bold text-slate-900 leading-tight">{product.name}</h3>
          <p className="text-xs text-slate-400">{product.id} · {product.category}</p>
        </div>
        <ModeBadge mode={product.currentMode} />
      </div>

      <div className="space-y-4">
        <PriceBar price={product.yourPrice} floor={product.priceFloor} ceiling={product.priceCeiling} />
        <StockLine stock={product.stock} reserve={product.stockReserveMin} />
        <SentimentGauge
          score={sentiment?.score24h}
          trend={sentiment?.trend}
          topSignal={sentiment?.topSignal}
        />
        <div className="pt-2 border-t border-slate-100">
          <CompetitorTable competitors={competitors} />
        </div>
      </div>
    </div>
  )
}

export default function Dashboard() {
  const [now, setNow] = useState(new Date())
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(t)
  }, [])

  const { data, isLoading, isError } = useQuery({
    queryKey: ['overview'],
    queryFn: () => api.get('/api/dashboard/overview').then((r) => r.data),
    refetchInterval: 30000,
  })

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Dashboard</h1>
          <p className="text-sm text-slate-500">{now.toLocaleString()}</p>
        </div>
        <RunAgentButton />
      </div>

      {isLoading && <p className="text-slate-500">Loading…</p>}
      {isError && <p className="text-red-600">Could not reach the backend at port 8080.</p>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {(data || []).map((ov) => (
          <ProductCard key={ov.product.id} overview={ov} />
        ))}
      </div>
    </div>
  )
}
