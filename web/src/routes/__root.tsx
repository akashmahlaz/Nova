import {
  HeadContent,
  Link,
  Scripts,
  createRootRouteWithContext,
} from '@tanstack/react-router'
import appCss from '../styles.css?url'
import type { QueryClient } from '@tanstack/react-query'

interface MyRouterContext {
  queryClient: QueryClient
}

export const Route = createRootRouteWithContext<MyRouterContext>()({
  head: () => ({
    meta: [
      { charSet: 'utf-8' },
      { name: 'viewport', content: 'width=device-width, initial-scale=1' },
      { title: 'Nova Ops — Admin Console' },
    ],
    links: [
      {
        rel: 'stylesheet',
        href: 'https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,500;9..144,700&family=Manrope:wght@400;500;600;700;800&display=swap',
      },
      { rel: 'stylesheet', href: appCss },
    ],
  }),
  notFoundComponent: () => (
    <div className="min-h-screen bg-background flex items-center justify-center p-6 text-foreground">
      <div className="max-w-sm w-full rounded-2xl bg-card border border-border p-6 text-center space-y-3">
        <div className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">404</div>
        <h1 className="text-lg font-bold">Page not found</h1>
        <p className="text-xs text-muted-foreground">The requested route does not exist in Nova Ops.</p>
        <Link
          to="/"
          className="inline-flex items-center justify-center h-9 px-4 rounded-lg bg-primary text-primary-foreground text-xs font-medium"
        >
          Back to Console
        </Link>
      </div>
    </div>
  ),
  shellComponent: RootDocument,
})

function RootDocument({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <HeadContent />
      </head>
      <body className="min-h-screen bg-background text-foreground font-sans antialiased">
        {children}
        <Scripts />
      </body>
    </html>
  )
}
