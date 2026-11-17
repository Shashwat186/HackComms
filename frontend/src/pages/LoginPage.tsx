import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type OtpRequested } from '../api';
import { useAuth } from '../auth';
import OtpInput from '../components/OtpInput';

const RESEND_SECONDS = 30;

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [sent, setSent] = useState<OtpRequested | null>(null);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  async function sendCode(e?: FormEvent) {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await api.requestOtp(email);
      setSent(res);
      setCode('');
      setCooldown(RESEND_SECONDS);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function verify(value: string) {
    if (!sent || value.length !== 6) return;
    setBusy(true);
    setError(null);
    try {
      const { token, user } = await api.verifyOtp(sent.email, value);
      login(token, user);
      navigate('/', { replace: true });
    } catch (err) {
      setError((err as Error).message);
      setCode('');
    } finally {
      setBusy(false);
    }
  }

  function onCodeChange(value: string) {
    setCode(value);
    if (error) setError(null);
    if (value.length === 6) void verify(value); // submit as soon as the last digit lands
  }

  return (
    <div className="login">
      <aside className="login__brand">
        <div className="logo">
          <span className="logo__mark">#</span> HackComms
        </div>
        <div className="login__pitch">
          <h1>Your college's hackathon HQ.</h1>
          <p>Hackathon info is scattered across a dozen WhatsApp groups. HackComms gives your college one verified place for it.</p>
          <ul className="features">
            <li><span>💬</span>A chat channel for every hackathon</li>
            <li><span>📅</span>All deadlines on one calendar</li>
            <li><span>🤝</span>Find teammates from your own campus</li>
            <li><span>🗳️</span>Fair, secret-ballot society elections</li>
          </ul>
        </div>
        <p className="login__foot">Only students with a college email can join.</p>
      </aside>

      <main className="login__panel">
        <div className="card">
          {!sent ? (
            <form onSubmit={sendCode} noValidate>
              <h2>Sign in</h2>
              <p className="muted">Use your college email. We'll send you a 6-digit code.</p>

              <label className="field">
                <span>College email</span>
                <input
                  type="email"
                  autoComplete="email"
                  autoFocus
                  placeholder="name@college.ac.in"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (error) setError(null);
                  }}
                  aria-invalid={!!error}
                  disabled={busy}
                />
              </label>

              {error && <p className="error" role="alert">{error}</p>}

              <button className="btn btn--primary" type="submit" disabled={busy || !email.includes('@')}>
                {busy ? 'Sending…' : 'Send code'}
              </button>
            </form>
          ) : (
            <div>
              <button className="link back" onClick={() => { setSent(null); setError(null); }}>
                ← Use a different email
              </button>

              <div className="workspace-chip">
                <div className="workspace-chip__avatar">{sent.workspaceName.slice(0, 2)}</div>
                <div>
                  <div className="workspace-chip__name">Joining {sent.workspaceName}</div>
                  <div className="workspace-chip__domain">{sent.workspaceDomain}</div>
                </div>
              </div>

              <h2>Check your inbox</h2>
              <p className="muted">
                We sent a code to <strong>{sent.email}</strong>. It expires in 10 minutes.
              </p>

              <OtpInput value={code} onChange={onCodeChange} disabled={busy} invalid={!!error} />

              {error && <p className="error" role="alert">{error}</p>}
              {busy && <p className="muted small">Verifying…</p>}

              <p className="muted small resend">
                Didn't get it?{' '}
                {cooldown > 0 ? (
                  <span>Resend in {cooldown}s</span>
                ) : (
                  <button className="link" onClick={() => sendCode()} disabled={busy}>Resend code</button>
                )}
              </p>

              {import.meta.env.DEV && (
                <p className="dev-hint">Dev mode: if SMTP isn't set up, the code is printed in the Spring Boot console.</p>
              )}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
