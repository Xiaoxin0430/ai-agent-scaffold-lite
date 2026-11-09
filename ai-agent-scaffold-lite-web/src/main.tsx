import React from 'react';
import ReactDOM from 'react-dom/client';
import DynamicTeamConsole from './DynamicTeamConsole';

function hasDemoLogin(): boolean {
  const value = document.cookie.split('; ').find((item) => item.startsWith('ai_agent_login='));
  if (!value) return false;
  try {
    const payload = JSON.parse(decodeURIComponent(value.slice(value.indexOf('=') + 1)));
    return Boolean(payload?.user);
  } catch {
    return false;
  }
}

if (!import.meta.env.DEV && !hasDemoLogin()) {
  window.location.replace('../login.html');
} else {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <DynamicTeamConsole />
    </React.StrictMode>,
  );
}
