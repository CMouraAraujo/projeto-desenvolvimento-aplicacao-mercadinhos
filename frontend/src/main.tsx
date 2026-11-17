import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './auth/Auth'
import { App } from './App'
import './styles.css'

const client = new QueryClient({ defaultOptions: {
  queries: { retry: false, staleTime: 30_000 },
  mutations: { retry: false },
} })
createRoot(document.getElementById('root')!).render(
  <StrictMode><QueryClientProvider client={client}><AuthProvider><App/></AuthProvider></QueryClientProvider></StrictMode>,
)
