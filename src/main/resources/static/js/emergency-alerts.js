// Staff-side emergency alerts, shared by the nurse (checkin.html) and doctor
// (doctor.html, schedule.html) pages, so an alert reaches staff on any screen.
// Every unresolved EMERGENCY_ALERT - sent by a student with one tap from their
// portal - pops up a full-screen red overlay and a red banner across the top
// of every view.
//  - "Acknowledge" records who is responding and hides the popup for one
//    minute. If the emergency still isn't done by then, it pops up again.
//  - Only "Mark emergency done" or an Emergency Check-in resolves the alert,
//    which takes it off every nurse/doctor screen.
//  - If the student cancels it (sent by mistake), each nurse/doctor gets an
//    amber "false alarm" notice to dismiss, instead of the alert silently
//    vanishing while someone may already be on their way.
// The page calls EmergencyAlerts.init() once and EmergencyAlerts.update(items)
// each time it polls /notifications.
const EmergencyAlerts = (() => {
  const SNOOZE_MS = 60 * 1000;
  // Cancellations older than this aren't announced (e.g. to someone who logs in hours later).
  const CANCEL_NOTICE_MS = 30 * 60 * 1000;
  const DISMISSED_KEY = 'dismissedCancelledEmergencyIds';
  const snoozedUntil = new Map(); // alert id -> time it may pop up again
  let opts = null;
  let active = [];
  let shownId = null;

  function init(options) {
    opts = options;
    const main = document.querySelector('.app-content');
    const banner = document.createElement('div');
    banner.id = 'sos-banner';
    banner.hidden = true;
    main.insertBefore(banner, main.firstChild);

    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay sos-overlay';
    overlay.id = 'sos-overlay';
    overlay.hidden = true;
    overlay.innerHTML = `
      <div class="modal-box sos-box" role="alertdialog" aria-labelledby="sos-name">
        <div class="sos-eyebrow">${HC.icon('alert')} Student emergency</div>
        <h2 id="sos-name"></h2>
        <p class="sos-sent" id="sos-sent"></p>
        <p class="sos-ack" id="sos-ack"></p>
        <p class="small" id="sos-message"></p>
        <div class="sos-actions">
          <button class="btn btn-danger" onclick="EmergencyAlerts.acknowledge()">Acknowledge — I’m responding</button>
          <button class="btn" onclick="EmergencyAlerts.checkIn()">Student arrived — check in</button>
          <button class="btn" onclick="EmergencyAlerts.resolve()">Mark emergency done</button>
        </div>
        <p class="tiny muted" style="margin-top:14px;">This will pop up again in 1 minute until it’s marked done.</p>
        <p class="tiny" id="sos-more"></p>
      </div>`;
    document.body.appendChild(overlay);

    const cancelOverlay = document.createElement('div');
    cancelOverlay.className = 'modal-overlay sos-cancel-overlay';
    cancelOverlay.id = 'sos-cancel-overlay';
    cancelOverlay.hidden = true;
    cancelOverlay.innerHTML = `
      <div class="modal-box sos-cancel-box" role="alertdialog" aria-labelledby="sos-cancel-name">
        <div class="sos-eyebrow">${HC.icon('alert')} Emergency cancelled</div>
        <h2 id="sos-cancel-name"></h2>
        <p class="sos-ack" id="sos-cancel-text"></p>
        <p class="small muted" id="sos-cancel-detail"></p>
        <div class="sos-actions">
          <button class="btn btn-primary" onclick="EmergencyAlerts.dismissCancelled()">OK</button>
        </div>
      </div>`;
    document.body.appendChild(cancelOverlay);
  }

  function loadDismissed() {
    try { return new Set(JSON.parse(localStorage.getItem(DISMISSED_KEY) || '[]')); } catch (e) { return new Set(); }
  }
  let dismissedCancelled = loadDismissed();
  let cancelShownId = null;

  function showNextCancelled(items) {
    if (cancelShownId !== null) return;
    const now = Date.now();
    const n = items.find(x => x.type === 'EMERGENCY_ALERT' && x.cancelledByStudent && !dismissedCancelled.has(x.id)
      && now - new Date(x.resolvedAt) < CANCEL_NOTICE_MS);
    if (!n) return;
    cancelShownId = n.id;
    document.getElementById('sos-cancel-name').textContent = (n.studentName || n.studentUsername) + ' (' + n.studentUsername + ')';
    document.getElementById('sos-cancel-text').textContent =
      `Cancelled by the student at ${HC.timeOf(n.resolvedAt)}. It was a false alarm, so no response is needed.`;
    document.getElementById('sos-cancel-detail').textContent = `The alert was sent at ${HC.timeOf(n.createdAt)}`
      + (n.acknowledgedBy ? ` and acknowledged by ${n.acknowledgedBy}.` : '.');
    document.getElementById('sos-cancel-overlay').hidden = false;
  }

  function dismissCancelled() {
    dismissedCancelled.add(cancelShownId);
    try { localStorage.setItem(DISMISSED_KEY, JSON.stringify([...dismissedCancelled].slice(-200))); } catch (e) {}
    cancelShownId = null;
    document.getElementById('sos-cancel-overlay').hidden = true;
    opts.refresh();
  }

  function update(items) {
    active = items.filter(n => n.activeEmergency);
    renderBanner();
    if (shownId !== null) {
      // Resolved elsewhere (another staff member, or the student cancelled).
      const current = active.find(n => n.id === shownId);
      if (current) fillPopup(current); else hidePopup();
    }
    if (shownId === null) {
      const now = Date.now();
      const next = active.find(n => (snoozedUntil.get(n.id) || 0) <= now);
      if (next) showPopup(next);
    }
    showNextCancelled(items);
  }

  function minutesAgo(iso) {
    const mins = Math.max(0, Math.round((Date.now() - new Date(iso)) / 60000));
    return mins === 0 ? 'just now' : mins === 1 ? '1 min ago' : mins + ' min ago';
  }

  function ackLabel(n) {
    return n.acknowledgedBy
      ? `Acknowledged by ${n.acknowledgedBy} at ${HC.timeOf(n.acknowledgedAt)} — not marked done yet`
      : 'Nobody has acknowledged this yet';
  }

  function fillPopup(n) {
    document.getElementById('sos-name').textContent = (n.studentName || n.studentUsername) + ' (' + n.studentUsername + ')';
    document.getElementById('sos-sent').textContent = `Sent at ${HC.timeOf(n.createdAt)} Fiji · ${minutesAgo(n.createdAt)}`;
    const ack = document.getElementById('sos-ack');
    ack.textContent = ackLabel(n);
    ack.classList.toggle('pending', !n.acknowledgedBy);
    document.getElementById('sos-message').textContent = n.message;
    const others = active.length - 1;
    document.getElementById('sos-more').textContent = others > 0
      ? `${others} other active emergenc${others === 1 ? 'y' : 'ies'} — see the red banner.` : '';
  }

  function showPopup(n) {
    shownId = n.id;
    fillPopup(n);
    document.getElementById('sos-overlay').hidden = false;
    beep();
  }

  function hidePopup() {
    shownId = null;
    document.getElementById('sos-overlay').hidden = true;
  }

  function shown() { return active.find(n => n.id === shownId); }

  // Hide the current popup for SNOOZE_MS; it returns on a later poll if the
  // emergency is still unresolved.
  function snoozeShown() {
    snoozedUntil.set(shownId, Date.now() + SNOOZE_MS);
    hidePopup();
  }

  function post(path) {
    return fetch('/notifications/' + path, { method: 'POST', headers: { 'Authorization': opts.authHeader() }, keepalive: true });
  }

  function acknowledge() {
    const n = shown();
    if (!n) return;
    snoozeShown();
    post(n.id + '/acknowledge').then(() => opts.refresh()).catch(() => {});
  }

  function checkIn() {
    const n = shown();
    if (!n) return;
    snoozeShown();
    if (!n.acknowledgedBy) post(n.id + '/acknowledge').catch(() => {});
    opts.onCheckIn(n.studentUsername);
  }

  function resolve(id) {
    const n = id !== undefined ? active.find(x => x.id === id) : shown();
    if (!n) return;
    if (!confirm(`Mark the emergency for ${n.studentName || n.studentUsername} as done? It will stop alerting every nurse and doctor.`)) return;
    if (shownId === n.id) hidePopup();
    post(n.id + '/resolve').then(() => opts.refresh()).catch(() => {});
  }

  // Re-open a snoozed alert straight away (from the banner or alert list).
  function open(id) {
    const n = active.find(x => x.id === id);
    if (!n) return false;
    snoozedUntil.delete(id);
    if (shownId !== null) hidePopup();
    showPopup(n);
    return true;
  }

  function renderBanner() {
    const banner = document.getElementById('sos-banner');
    if (!active.length) { banner.hidden = true; banner.innerHTML = ''; return; }
    banner.innerHTML = active.map(n => `
      <div class="sos-banner-row">
        <span class="sos-dot"></span>
        <div class="sos-banner-text">
          <strong>EMERGENCY · ${HC.esc(n.studentName || n.studentUsername)} (${HC.esc(n.studentUsername)})</strong>
          <span>Sent ${HC.esc(HC.timeOf(n.createdAt))} · ${HC.esc(ackLabel(n))}</span>
        </div>
        <button class="btn btn-sm" onclick="EmergencyAlerts.open(${n.id})">Open</button>
      </div>`).join('');
    banner.hidden = false;
  }

  // A short alarm tone. Browsers only allow sound after the user has clicked
  // somewhere on the page, so this silently does nothing until then.
  let audioCtx = null;
  function beep() {
    try {
      audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
      [0, 0.35, 0.7].forEach(offset => {
        const osc = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        osc.type = 'square';
        osc.frequency.value = 880;
        gain.gain.value = 0.08;
        osc.connect(gain).connect(audioCtx.destination);
        osc.start(audioCtx.currentTime + offset);
        osc.stop(audioCtx.currentTime + offset + 0.2);
      });
    } catch (e) {}
  }

  return { init, update, acknowledge, checkIn, resolve, open, dismissCancelled };
})();
