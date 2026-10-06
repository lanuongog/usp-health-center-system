const VAL = {
  _last: {},

  // ---- styled pop-up dialogs (same look as the booking confirmation) ----
  _css() {
    if (document.getElementById('vq-style')) return;
    const st = document.createElement('style'); st.id = 'vq-style';
    st.textContent = `
.vq-overlay{position:fixed;inset:0;background:rgba(20,33,61,.45);display:flex;align-items:flex-start;justify-content:center;padding:40px 16px;overflow-y:auto;z-index:10000;font-family:inherit}
.vq-box{background:#fff;border-radius:10px;width:100%;max-width:420px;box-shadow:0 12px 32px rgba(20,33,61,.25);color:#14213D}
.vq-head{padding:16px 18px;border-bottom:1px solid #D8E6E6;display:flex;align-items:center;justify-content:space-between}
.vq-head h2{font-size:15px;margin:0}
.vq-x{background:transparent;border:none;font-size:18px;line-height:1;color:#5C7070;cursor:pointer;padding:4px}
.vq-body{padding:16px 18px}
.vq-row{display:flex;justify-content:space-between;gap:14px;padding:7px 0;border-bottom:1px solid #D8E6E6;font-size:13px}
.vq-row:last-of-type{border-bottom:none}
.vq-row span{color:#5C7070}.vq-row strong{text-align:right;word-break:break-word}
.vq-note{font-size:12.5px;color:#5C7070;margin:12px 0 0;white-space:pre-wrap}
.vq-note.vq-big{color:#14213D;font-size:14px;margin:0}
.vq-lab{display:block;font-size:12.5px;font-weight:600;margin:0 0 6px}
.vq-in{width:100%;box-sizing:border-box;padding:9px 10px;border:1px solid #D8E6E6;border-radius:6px;font:inherit;font-size:13.5px}
.vq-err{color:#B23A2E;font-size:12.5px;min-height:16px;margin-top:8px}
.vq-btns{display:flex;gap:8px;margin-top:14px}
.vq-ok{flex:1;background:#0D7377;color:#fff;border:none;padding:10px;border-radius:6px;font-size:14px;font-weight:600;cursor:pointer}
.vq-ok:hover{background:#095558}.vq-ok.vq-danger{background:#B23A2E}.vq-ok.vq-danger:hover{background:#8f2d23}
.vq-cancel{padding:10px 16px;border-radius:6px;font-size:13px;font-weight:600;cursor:pointer;border:1px solid #D8E6E6;background:#fff;color:#0D7377}
.vq-cancel:hover{background:#E3F1F1}`;
    document.head.appendChild(st);
  },
  _dialog(o) {
    this._css();
    return new Promise(resolve => {
      const ov = document.createElement('div'); ov.className = 'vq-overlay';
      const rows = (o.rows || []).map(r => '<div class="vq-row"><span></span><strong></strong></div>').join('');
      ov.innerHTML = '<div class="vq-box" role="dialog" aria-modal="true"><div class="vq-head"><h2></h2><button class="vq-x" aria-label="Close">✕</button></div><div class="vq-body">' + rows +
        (o.note ? '<p class="vq-note"></p>' : '') +
        (o.input ? '<label class="vq-lab"></label><' + (o.input.multiline ? 'textarea rows="3"' : 'input') + ' class="vq-in">' + (o.input.multiline ? '</textarea>' : '') : '') +
        '<div class="vq-err"></div><div class="vq-btns"><button class="vq-ok"></button><button class="vq-cancel"></button></div></div></div>';
      ov.querySelector('h2').textContent = o.title || 'Please confirm';
      (o.rows || []).forEach((r, i) => {
        const el = ov.querySelectorAll('.vq-row')[i];
        el.querySelector('span').textContent = r[0]; el.querySelector('strong').textContent = r[1];
      });
      if (o.note) ov.querySelector('.vq-note').textContent = o.note;
      if (o.noCancel && o.note) ov.querySelector('.vq-note').classList.add('vq-big');
      const inp = ov.querySelector('.vq-in');
      if (o.input) {
        ov.querySelector('.vq-lab').textContent = o.input.label || '';
        inp.type = o.input.type || 'text';
        if (o.input.maxlength) inp.maxLength = o.input.maxlength;
        if (o.input.placeholder) inp.placeholder = o.input.placeholder;
        if (o.input.value) inp.value = o.input.value;
      }
      const ok = ov.querySelector('.vq-ok'), cancel = ov.querySelector('.vq-cancel'), err = ov.querySelector('.vq-err');
      ok.textContent = o.okText || 'Yes, confirm'; cancel.textContent = o.cancelText || 'Cancel';
      if (o.noCancel) cancel.style.display = 'none';
      if (o.danger) ok.classList.add('vq-danger');
      const done = v => { document.removeEventListener('keydown', onKey); ov.remove(); resolve(v); };
      const submit = () => {
        if (o.input) {
          const v = o.input.type === 'password' ? inp.value : inp.value.trim();
          if (o.input.required && !v) { err.textContent = (o.input.label || 'This field') + ' is required.'; return; }
          const bad = o.input.validate ? o.input.validate(v) : null;
          if (bad) { err.textContent = bad; return; }
          done(v);
        } else done(true);
      };
      const onKey = e => { if (e.key === 'Escape') done(o.input ? null : false); if (e.key === 'Enter' && !(o.input && o.input.multiline)) { e.preventDefault(); submit(); } };
      document.addEventListener('keydown', onKey);
      ok.onclick = submit; cancel.onclick = () => done(o.input ? null : false);
      ov.querySelector('.vq-x').onclick = cancel.onclick;
      ov.addEventListener('mousedown', e => { if (e.target === ov) done(o.input ? null : false); });
      document.body.appendChild(ov);
      (inp || ok).focus();
    });
  },
  ask(o) { return this._dialog(o); },            // resolves true / false
  askInput(o) { return this._dialog(o); },       // resolves the typed text, or null if cancelled
  // pop-up for validation problems / errors (single OK button)
  alert(message, title) {
    if (document.querySelector('.vq-overlay')) return Promise.resolve();
    return this._dialog({ title: title || 'Please check this', note: message, okText: 'OK', noCancel: true, danger: true });
  },
  // Any red error message that appears on the page is shown as a pop-up as well
  _watch() {
    if (this._watching) return; this._watching = true;
    const isRed = el => { const c = getComputedStyle(el).color; return c === 'rgb(176, 58, 46)' || c === 'rgb(178, 58, 46)' || c === 'rgb(192, 57, 43)'; };
    const isErr = el => el.classList.contains('err') || el.classList.contains('error') || /(^|\s)error(\s|$)/.test(el.className || '') || isRed(el);
    const check = node => {
      let el = node && node.nodeType === 3 ? node.parentElement : node;
      if (!el || !el.closest || el.closest('.vq-overlay') || el.closest('script,style')) return;
      for (let i = 0; i < 3 && el && el !== document.body; i++, el = el.parentElement) {
        const text = (el.innerText || el.textContent || '').trim();
        if (!text) { el._vqLast = ''; continue; }
        if (el.children.length > 2 || text.length > 400) continue;
        // only real messages: ignore red numbers/labels (stats, badges, table cells)
        if (text.length < 12 || !/[A-Za-z]{3,}\s+[A-Za-z]{2,}/.test(text) || el.closest('table,.stat,.card-value,.badge')) continue;
        if (el.offsetParent === null && getComputedStyle(el).position !== 'fixed') continue;
        if (isErr(el) && el._vqLast !== text) { el._vqLast = text; VAL.alert(text); return; }
      }
    };
    new MutationObserver(ms => ms.forEach(m => check(m.type === 'childList' ? (m.addedNodes[0] || m.target) : m.target)))
      .observe(document.body, { childList: true, characterData: true, subtree: true, attributes: true, attributeFilter: ['class', 'style'] });
  },
  // returns false if the same action fired again within ms (stops double-click submits)
  once(key, ms) {
    const now = Date.now();
    if (this._last[key] && now - this._last[key] < (ms || 2000)) return false;
    this._last[key] = now;
    return true;
  },
  today() { return new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10); },
  vitals(temp, bp) {
    const t = Number(String(temp).trim());
    if (String(temp).trim() === '' || !isFinite(t)) return 'Temperature must be a number in degrees Celsius (e.g. 37.2).';
    if (t < 30 || t > 43) return 'Temperature is out of the valid range (30\u201343 \u00B0C).';
    const m = /^(\d{2,3})\s*\/\s*(\d{2,3})$/.exec(String(bp).trim());
    if (!m) return 'Blood pressure must be in the format systolic/diastolic (e.g. 120/80).';
    const s = parseInt(m[1], 10), d = parseInt(m[2], 10);
    if (s < 60 || s > 250 || d < 40 || d > 150) return 'Blood pressure is out of the valid range (systolic 60\u2013250, diastolic 40\u2013150).';
    if (d >= s) return 'Systolic pressure must be higher than diastolic pressure.';
    return null;
  },
  password(pw, username) {
    if (!pw) return 'A password is required.';
    if (pw.length < 6 || pw.length > 100) return 'Password must be between 6 and 100 characters.';
    if (!/[A-Za-z]/.test(pw) || !/\d/.test(pw)) return 'Password must contain at least one letter and one number.';
    if (/\s/.test(pw)) return 'Password cannot contain spaces.';
    if (username && pw.toLowerCase() === String(username).toLowerCase()) return 'Password cannot be the same as the username.';
    return null;
  }
};


// ---- 401 handler: signed out / expired -> own pop-up, then back to sign-in (not on login page) ----
(function () {
  if (/login\.html$/.test(location.pathname) || location.pathname === '/' || /index\.html$|services\.html$/.test(location.pathname)) return;
  const _fetch = window.fetch.bind(window);
  let shown = false;
  window.fetch = async function () {
    const res = await _fetch.apply(null, arguments);
    if (res.status === 401 && !shown) {
      shown = true;
      try { localStorage.clear(); sessionStorage.clear(); } catch (e) {}
      VAL.alert('Your session has expired or you are not signed in. Please sign in again.', 'Signed out').then(() => { window.location.href = '/login.html'; });
    }
    return res;
  };
})();

// ---- Forgot password: ID -> emailed code -> new password (each step is a pop-up) ----
VAL.forgotPassword = async function (prefill) {
  const post = async (url, body) => {
    try {
      const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      const d = await r.json().catch(() => ({}));
      return { ok: r.ok && !d.error, data: d };
    } catch (e) { return { ok: false, data: { error: 'Service temporarily unavailable' } }; }
  };
  // Step 1: ID
  const id = await VAL.askInput({ title: 'Forgot password', note: 'Enter your Student ID or staff username. A 6-digit code will be sent to your USP email address (valid for 15 minutes).',
    input: { label: 'Student ID or username', required: true, maxlength: 50, placeholder: 'e.g. S12345678', value: prefill,
      validate: v => /^[A-Za-z0-9._@-]{3,50}$/.test(v) ? null : 'Use 3-50 letters, numbers, . _ - only.' }, okText: 'Send code' });
  if (!id) return;
  const sent = await post('/auth/forgot-password', { username: id });
  if (!sent.ok) { await VAL.alert(sent.data.error || 'Unable to send a reset code right now.'); return; }
  // Step 2: code
  let code = null;
  for (let tries = 0; tries < 6 && !code; tries++) {
    const c = await VAL.askInput({ title: 'Enter your code', note: (sent.data.message || 'If the account exists, a code has been sent.') + '\nEnter the 6-digit code from your email.',
      input: { label: '6-digit code', required: true, maxlength: 6, placeholder: '123456', validate: v => /^\d{6}$/.test(v) ? null : 'The code is exactly 6 digits.' }, okText: 'Verify code' });
    if (!c) return;
    const chk = await post('/auth/verify-code', { username: id, code: c });
    if (chk.ok) code = c; else await VAL.alert(chk.data.error || 'That code is not valid.');
  }
  if (!code) return;
  // Step 3: new password (+ confirm)
  const pw = await VAL.askInput({ title: 'New password', note: 'Choose a new password: 6-100 characters, at least one letter and one number, no spaces.',
    input: { label: 'New password', type: 'password', required: true, maxlength: 100, validate: v => VAL.password(v, id) }, okText: 'Next' });
  if (!pw) return;
  const pw2 = await VAL.askInput({ title: 'Confirm password', note: 'Type the new password again.',
    input: { label: 'Confirm new password', type: 'password', required: true, maxlength: 100, validate: v => v === pw ? null : 'The two passwords do not match.' }, okText: 'Reset password' });
  if (!pw2) return;
  const res = await post('/auth/reset-password', { username: id, code, newPassword: pw });
  if (!res.ok) { await VAL.alert(res.data.error || 'Unable to reset the password.'); return; }
  await VAL._dialog({ title: 'Password reset', note: res.data.message || 'Your password has been reset. You can now sign in.', okText: 'OK', noCancel: true });
};
