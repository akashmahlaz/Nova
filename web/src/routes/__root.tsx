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
      { title: 'Nova Ops' },
    ],
    links: [
      {
        rel: 'stylesheet',
        href: 'https://fonts.googleapis.com/css2?family=Roboto+Flex:opsz,wght@8..144,400;8..144,500;8..144,600&family=Roboto+Mono:wght@400;500&display=swap',
      },
      { rel: 'stylesheet', href: appCss },
    ],
  }),
  notFoundComponent: () => (
    <div className="min-h-screen bg-canvas flex items-center justify-center p-6 text-fg">
      <div className="max-w-sm w-full rounded-2xl bg-surface border border-line p-6 text-center space-y-3">
        <div className="font-mono text-xs text-fg-3">404</div>
        <h1 className="text-lg font-medium">Page not found</h1>
        <p className="text-sm text-fg-2">This address isn't part of Nova Ops.</p>
        <Link
          to="/"
          className="inline-flex items-center justify-center h-9 px-4 rounded-lg bg-fg text-surface text-sm font-medium"
        >
          Back to overview
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
      <body className="min-h-screen bg-canvas text-fg font-sans antialiased">
        {children}
        <Scripts />
      </body>
    </html>
  )
}
