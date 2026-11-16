import type { Metadata, Viewport } from 'next';
import './globals.css';
export const metadata: Metadata = {
  title: 'QuizGate | Learn a little. Play a lot.',
  description: 'Small learning moments before playtime.',
  applicationName: 'QuizGate',
  manifest: '/site.webmanifest',
  icons: { icon: [{ url: '/favicon.svg', type: 'image/svg+xml' }, { url: '/favicon.ico' }], apple: '/apple-touch-icon.png' },
  openGraph: { title: 'QuizGate', description: 'Learn a little. Play a lot.', images: ['/social-og-1200x630.png'] },
  twitter: { card: 'summary_large_image', images: ['/social-og-1200x630.png'] },
};
export const viewport: Viewport = { width: 'device-width', initialScale: 1, themeColor: '#0A91D9' };
export default function RootLayout({ children }: Readonly<{children: React.ReactNode}>) {
  return <html lang="en" data-theme="sunshine"><body>{children}</body></html>;
}
