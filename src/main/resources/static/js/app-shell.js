// Shared app shell for the role workspaces: navy sidebar, white top bar,
// mobile top bar + drawer, optional student bottom nav, and hash-based views
// (<section data-view="name">). Also a few formatting helpers (HC.*) used by
// every workspace page.
(function () {
  const FIJI_TZ = 'Pacific/Fiji';

  const ICONS = {
    home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"/></svg>',
    book: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18M12 13v5M9.5 15.5h5"/></svg>',
    visits: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M9 14l2 2 4-4"/></svg>',
    bell: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>',
    menu: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 6h16M4 12h16M4 18h16"/></svg>',
    calendarPlus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18M12 13v5M9.5 15.5h5"/></svg>',
    refresh: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-2.64-6.36M21 3v6h-6"/></svg>',
    alert: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.5h.01"/></svg>',
    clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
    calendarCheck: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18M9 15l2 2 4-4"/></svg>',
    file: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/></svg>',
    check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5 9-10"/></svg>',
    arrow: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M13 6l6 6-6 6"/></svg>',
    download: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v11M7 10l5 5 5-5M5 20h14"/></svg>',
    userPlus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="4"/><path d="M2 21a7 7 0 0 1 14 0M19 8v6M16 11h6"/></svg>'
  };

  // ---------- Formatting helpers ----------
  const HC = {
    icon(name) { return ICONS[name] || ''; },

    esc(str) {
      if (str === null || str === undefined) return '';
      return String(str)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
    },

    // "09:00 AM - 09:30 AM" -> { start: "09:00", end: "09:30", startMin: 540 }
    parseSlot(timeSlot) {
      const parts = String(timeSlot || '').split('-').map(s => s.trim());
      const toMin = t => {
        const m = (t || '').match(/(\d{1,2}):(\d{2})\s*(AM|PM)?/i);
        if (!m) return null;
        let h = parseInt(m[1], 10) % (m[3] ? 12 : 24);
        if (m[3] && m[3].toUpperCase() === 'PM') h += 12;
        return h * 60 + parseInt(m[2], 10);
      };
      const fmt = mins => mins === null ? '' : String(Math.floor(mins / 60)).padStart(2, '0') + ':' + String(mins % 60).padStart(2, '0');
      const s = toMin(parts[0]);
      const e = toMin(parts[1]);
      return { start: fmt(s), end: fmt(e), startMin: s === null ? 0 : s, endMin: e };
    },

    slotRange(timeSlot) {
      const p = HC.parseSlot(timeSlot);
      if (!p.start) return timeSlot || '';
      return p.end ? p.start + '–' + p.end : p.start;
    },

    // 24h "14:00" -> "02:00 PM" (the format the schedule API stores)
    to12h(hhmm) {
      const [h, m] = hhmm.split(':').map(Number);
      const suffix = h >= 12 ? 'PM' : 'AM';
      const h12 = h % 12 === 0 ? 12 : h % 12;
      return String(h12).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ' ' + suffix;
    },

    parseDate(iso) {
      if (!iso) return null;
      const [y, m, d] = String(iso).slice(0, 10).split('-').map(Number);
      return new Date(y, m - 1, d);
    },

    isoDate(d) {
      return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    },

    todayIso() { return HC.isoDate(new Date()); },

    // style: 'short' Tue 6 Oct | 'long' Mon 12 Oct 2026 | 'day' 22 Sep 2026 | 'full' 22 September 2026 | 'dm' 22 Sep
    fmtDate(iso, style) {
      const d = iso instanceof Date ? iso : HC.parseDate(iso);
      if (!d || isNaN(d)) return iso || '';
      const wd = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][d.getDay()];
      const moLong = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'][d.getMonth()];
      const mo = moLong.slice(0, 3);
      switch (style) {
        case 'long': return `${wd} ${d.getDate()} ${mo} ${d.getFullYear()}`;
        case 'day': return `${d.getDate()} ${mo} ${d.getFullYear()}`;
        case 'full': return `${d.getDate()} ${moLong} ${d.getFullYear()}`;
        case 'dm': return `${d.getDate()} ${mo}`;
        default: return `${wd} ${d.getDate()} ${mo}`;
      }
    },

    fmtDateTime(isoDateTime) {
      if (!isoDateTime) return '';
      const d = new Date(isoDateTime);
      if (isNaN(d)) return isoDateTime;
      const today = HC.todayIso() === HC.isoDate(d);
      const time = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
      return (today ? 'Today' : HC.fmtDate(d, 'long')) + ' · ' + time + ' Fiji';
    },

    timeOf(isoDateTime) {
      if (!isoDateTime) return '';
      const d = new Date(isoDateTime);
      if (isNaN(d)) return '';
      return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
    },

    nowLabel() {
      const now = new Date();
      const date = now.toLocaleDateString('en-GB', { timeZone: FIJI_TZ, weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }).replace(/,/g, '');
      const time = now.toLocaleTimeString('en-GB', { timeZone: FIJI_TZ, hour: '2-digit', minute: '2-digit', hour12: false });
      return { date, time, full: `${date} · ${time} Fiji` };
    },

    nowMinutes() { const n = new Date(); return n.getHours() * 60 + n.getMinutes(); },

    // A slot is past if its date is before today, or it's today and its start time has gone by.
    isSlotPast(iso, timeSlot) {
      const today = HC.todayIso();
      if (!iso || iso < today) return true;
      if (iso > today) return false;
      return HC.parseSlot(timeSlot).startMin <= HC.nowMinutes();
    },

    statusPill(status) {
      const map = {
        BOOKED: ['teal', 'Booked'], CHECKED_IN: ['amber', 'Checked in'], COMPLETED: ['teal', 'Completed'],
        CANCELLED: ['red', 'Cancelled'], NO_SHOW: ['', 'No-show']
      };
      const [cls, label] = map[status] || ['', status || ''];
      return `<span class="pill ${cls}">${HC.esc(label)}</span>`;
    },

    feePill(u) {
      if (u.feePaid) return '<span class="pill teal">Paid &amp; verified</span>';
      if (u.hasReceipt) return '<span class="pill amber">Pending verification</span>';
      return '<span class="pill red">Not paid</span>';
    }
  };
  window.HC = HC;

  // Sidebar menus for staff roles. Pages shared by more than one role (check-in,
  // schedule) show the menu of whoever is signed in.
  const STAFF = {
    NURSE: {
      workspace: 'Nurse workspace',
      nav: [
        { key: 'checkin', label: 'Check-in workspace', href: '/checkin.html#checkin' },
        { key: 'emergencies', label: 'Emergency requests', href: '/checkin.html#emergencies', count: true },
        { key: 'records', label: 'All records', href: '/checkin.html#records' },
        { key: 'directory', label: 'Student directory', href: '/checkin.html#directory' }
      ]
    },
    DOCTOR: {
      workspace: 'Doctor workspace',
      nav: [
        { key: 'queue', label: 'Queue & consultation', href: '/doctor.html#queue' },
        { key: 'schedule', label: 'My schedule', href: '/schedule.html' },
        { key: 'records', label: 'All records', href: '/doctor.html#records' },
        { key: 'directory', label: 'Student directory', href: '/doctor.html#directory' },
        { key: 'reports', label: 'My reports', href: '/doctor.html#reports' }
      ]
    },
    ADMIN: {
      workspace: 'Admin workspace',
      nav: [
        { key: 'dashboard', label: 'Dashboard', href: '/admin.html#dashboard' },
        { key: 'accounts', label: 'Accounts', href: '/admin.html#accounts' },
        { key: 'fees', label: 'Fees', href: '/admin.html#fees' },
        { key: 'schedules', label: 'Schedules', href: '/schedule.html' },
        { key: 'reports', label: 'Aggregate reports', href: '/admin.html#reports' },
        { key: 'audit', label: 'Audit log', href: '/admin.html#audit' },
        { key: 'checkin', label: 'Check-in workspace', href: '/checkin.html#checkin' }
      ]
    }
  };

  // ---------- Shell ----------
  const Shell = {
    opts: null,
    views: [],

    staff(role) { return STAFF[role]; },

    mount(opts) {
      this.opts = opts;
      document.querySelectorAll('[data-icon]').forEach(el => { el.outerHTML = HC.icon(el.dataset.icon); });
      const app = document.querySelector('.app');
      const main = app.querySelector('.app-main');

      // Sidebar
      const sidebar = document.createElement('aside');
      sidebar.className = 'sidebar';
      sidebar.innerHTML = `
        <a class="side-brand" href="/"><strong>USU</strong><span>Health Center</span><small>Laucala Campus</small></a>
        <div class="side-label">${HC.esc(opts.workspace)}</div>
        <nav class="side-nav">
          ${opts.nav.map(n => `<a href="${n.href}" data-key="${n.key}">${HC.esc(n.label)}${n.count ? ' <span data-count></span>' : ''}</a>`).join('')}
        </nav>
        <div class="side-foot">
          ${opts.foot ? `<p>${opts.foot}</p>` : ''}
          <button type="button" data-act="logout">Sign out →</button>
        </div>`;
      app.insertBefore(sidebar, main);

      const scrim = document.createElement('div');
      scrim.className = 'drawer-scrim';
      scrim.addEventListener('click', () => document.body.classList.remove('drawer-open'));
      app.appendChild(scrim);

      // Desktop top bar. alertsLabel renames the alerts button; alertsFirst puts
      // it before the account name so the name sits at the far right; alertsIcon
      // shows a bell with a count bubble instead of the text label.
      const alertsLabel = opts.alertsLabel || 'Alerts';
      const bell = opts.alertsIcon
        ? `<button type="button" id="notif-bell" class="notif-icon" aria-label="${HC.esc(alertsLabel)}" title="${HC.esc(alertsLabel)}">${ICONS.bell}<span id="notif-badge"></span></button>`
        : `<button type="button" id="notif-bell">${HC.esc(alertsLabel)}<span id="notif-badge"></span></button>`;
      const account = `
          <div id="account-menu-wrap">
            <button type="button" id="account-menu-btn"><span id="who-label">${HC.esc(opts.user || '')}</span></button>
            <div id="account-menu" hidden>
              <button type="button" data-act="password">Change password</button>
              <button type="button" data-act="logout">Sign out</button>
            </div>
          </div>`;
      const notif = `
          <div id="notif-wrap">
            ${bell}
            <div id="notif-panel" hidden></div>
          </div>`;
      const sep = opts.alertsIcon ? '' : '<span aria-hidden="true">·</span>';
      const topbar = document.createElement('header');
      topbar.className = 'app-topbar';
      topbar.innerHTML = `
        <div class="tb-left">USU · Laucala Campus</div>
        <div class="tb-center" data-clock></div>
        <div class="tb-right">${opts.alertsFirst ? notif + sep + account : account + sep + notif}
        </div>`;
      main.insertBefore(topbar, main.firstChild);
      if (opts.alerts === 'none') {
        topbar.querySelector('#notif-wrap').hidden = true;
        const sepEl = topbar.querySelector('.tb-right > span[aria-hidden]');
        if (sepEl) sepEl.hidden = true;
      }

      // Mobile top bar
      const mobile = document.createElement('div');
      mobile.className = 'mobile-bar';
      mobile.innerHTML = `
        <a class="mb-brand" href="/"><strong>USU</strong>Health Center</a>
        <div class="mb-actions">
          ${opts.alerts === 'none' ? '' : `<button type="button" data-act="alerts" aria-label="${HC.esc(alertsLabel)}">${ICONS.bell}<span data-mobile-count></span></button>`}
          <button type="button" data-act="menu" aria-label="Menu">${ICONS.menu}</button>
        </div>`;
      main.insertBefore(mobile, main.firstChild);

      if (opts.bottomNav) {
        const bn = document.createElement('nav');
        bn.className = 'bottom-nav';
        bn.innerHTML = opts.bottomNav.map(b =>
          `<a href="${b.href}" data-key="${b.key}">${ICONS[b.icon] || ''}<span>${HC.esc(b.label)}</span></a>`).join('');
        document.body.appendChild(bn);
        document.body.classList.add('has-bottom-nav');
      }

      // Wiring
      document.addEventListener('click', e => {
        const act = e.target.closest('[data-act]');
        if (act) {
          const a = act.dataset.act;
          if (a === 'logout') { if (window.logout) window.logout(); }
          if (a === 'password') { Shell.closeMenus(); if (window.openChangePasswordModal) window.openChangePasswordModal(); }
          if (a === 'menu') document.body.classList.toggle('drawer-open');
          if (a === 'alerts') Shell.openAlerts();
        }
        if (e.target.closest('#account-menu-btn')) {
          const m = document.getElementById('account-menu');
          m.hidden = !m.hidden;
        } else if (!e.target.closest('#account-menu')) {
          document.getElementById('account-menu').hidden = true;
        }
        if (e.target.closest('#notif-bell')) {
          Shell.openAlerts();
        } else if (!e.target.closest('#notif-panel') && opts.alerts === 'panel') {
          document.getElementById('notif-panel').hidden = true;
        }
        if (e.target.closest('.side-nav a')) document.body.classList.remove('drawer-open');
      });

      const tick = () => document.querySelectorAll('[data-clock]').forEach(el => { el.textContent = HC.nowLabel().full; });
      tick();
      setInterval(tick, 30000);

      // Views
      this.views = [...document.querySelectorAll('[data-view]')].map(v => v.dataset.view);
      window.addEventListener('hashchange', () => Shell.applyRoute());
      this.applyRoute();
    },

    openAlerts() {
      if (this.opts.alerts === 'panel') {
        const p = document.getElementById('notif-panel');
        if (p.hidden && window.loadNotifPanel) window.loadNotifPanel();
        p.hidden = !p.hidden;
        if (window.innerWidth <= 900) {
          // On phones the top bar is hidden, so send staff to their alert list instead.
          p.hidden = true;
          if (this.opts.mobileAlertsHref) location.href = this.opts.mobileAlertsHref;
          else { p.hidden = false; document.getElementById('notif-wrap').scrollIntoView(); }
        }
      } else if (this.opts.alertsHref) {
        location.href = this.opts.alertsHref;
      }
    },

    closeMenus() {
      document.getElementById('account-menu').hidden = true;
    },

    currentView() {
      const h = location.hash.replace('#', '').split('?')[0];
      return this.views.includes(h) ? h : (this.opts.defaultView || this.views[0]);
    },

    hashParam(name) {
      const q = location.hash.split('?')[1];
      return q ? new URLSearchParams(q).get(name) : null;
    },

    applyRoute() {
      const view = this.currentView();
      document.querySelectorAll('[data-view]').forEach(v => { v.hidden = v.dataset.view !== view; });
      const path = location.pathname === '/' ? '/index.html' : location.pathname;
      document.querySelectorAll('.side-nav a, .bottom-nav a').forEach(a => {
        const url = new URL(a.getAttribute('href'), location.href);
        const samePage = url.pathname === path;
        const linkView = url.hash.replace('#', '');
        const forced = this.opts.activeKey && a.dataset.key === this.opts.activeKey;
        a.classList.toggle('active', forced || (samePage && (!linkView || linkView === view)));
      });
      window.scrollTo(0, 0);
      if (this.opts.onView) this.opts.onView(view);
    },

    setUser(name) {
      const el = document.getElementById('who-label');
      if (el) el.textContent = name;
    },

    setAlertCount(n) {
      const badge = document.getElementById('notif-badge');
      if (badge) badge.textContent = n ? n : '';
      document.querySelectorAll('[data-count]').forEach(el => { el.textContent = n ? '· ' + n : ''; });
      document.querySelectorAll('[data-mobile-count]').forEach(el => { el.textContent = n ? n : ''; });
    }
  };
  window.Shell = Shell;
})();
