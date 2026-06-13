import { useMutation, useQueryClient } from '@tanstack/react-query'
import api from '../api/client.js'

export default function RunAgentButton() {
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => api.post('/api/agent/run-all').then((r) => r.data),
    onSuccess: () => {
      // Refresh everything the cycle could have changed.
      queryClient.invalidateQueries({ queryKey: ['overview'] })
      queryClient.invalidateQueries({ queryKey: ['actions'] })
      queryClient.invalidateQueries({ queryKey: ['blocks'] })
      queryClient.invalidateQueries({ queryKey: ['brief'] })
    },
  })

  return (
    <div className="flex items-center gap-3">
      <button
        onClick={() => mutation.mutate()}
        disabled={mutation.isPending}
        className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white font-medium text-sm hover:bg-indigo-700 disabled:opacity-60 transition"
      >
        {mutation.isPending ? (
          <>
            <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
            Running…
          </>
        ) : (
          <>▶ Run Agent Cycle</>
        )}
      </button>
      {mutation.isError && <span className="text-sm text-red-600">Cycle failed — is the backend running?</span>}
      {mutation.isSuccess && !mutation.isPending && (
        <span className="text-sm text-green-600">Cycle complete.</span>
      )}
    </div>
  )
}
