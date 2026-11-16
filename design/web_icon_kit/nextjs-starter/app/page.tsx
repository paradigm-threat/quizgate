export default function Page() {
  return <main style={{ maxWidth: 850, margin: '10vh auto', padding: 24 }}>
    <p style={{ fontWeight: 800, color: 'var(--primary)' }}>QUIZGATE</p>
    <h1 style={{ fontSize: 'clamp(40px,6vw,70px)', lineHeight: 1.1, letterSpacing: '-.05em' }}>Learn a little. Play a lot.</h1>
    <p>A friendly moment of learning before playtime.</p>
    <img alt="QuizGate icon" src="/pwa-192.png" width="144" height="144" style={{ borderRadius: 28, marginTop: 28 }} />
  </main>;
}
