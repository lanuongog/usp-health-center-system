// "Doctor absences" card, shared by the nurse check-in workspace (today and the
// next few days) and the admin dashboard (upcoming and the past month). Lists
// each day a doctor was marked unavailable and the students whose appointments
// were cancelled, with their phone and email so staff can reach anyone who may
// not open the portal in time. "Contacted" is shared by every nurse and admin,
// so two people don't call the same student. The card hides itself when there
// are no absences in range.
const DoctorAbsences = (() => {
  let opts = null;
  let absences = [];

  // options: { el, authHeader: () => string, daysBack, daysAhead }
  function mount(options) {
    opts = options;
    load();
  }

  function shiftIso(days) {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return HC.isoDate(d);
  }

  function load() {
    if (!opts) return;
    fetch(`/absences?from=${shiftIso(-opts.daysBack)}&to=${shiftIso(opts.daysAhead)}`, { headers: { 'Authorization': opts.authHeader() } })
      .then(res => res.ok ? res.json() : Promise.reject())
      .then(list => { absences = list; render(); })
      .catch(() => {});
  }

  function doctorLabel(name) {
    const m = String(name || '').trim().match(/^dr\.?\s*(.+)$/i);
    if (!m) return name || '';
    return 'Dr ' + m[1].split(/[\s._]+/).map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
  }

  function plural(n, word) { return `${n} ${word}${n === 1 ? '' : 's'}`; }

  function render() {
    const el = opts.el;
    if (!absences.length) { el.hidden = true; el.innerHTML = ''; return; }
    const today = HC.todayIso();
    // Upcoming first (soonest first), then past days (most recent first).
    const upcoming = absences.filter(a => a.date >= today);
    const past = absences.filter(a => a.date < today).reverse();
    const toContact = upcoming.reduce((n, a) => n + a.students.filter(s => !s.contacted).length, 0);

    el.innerHTML = `
      <div class="absence-head">
        <h2>Doctor absences</h2>
        ${toContact ? `<span class="pill amber">${plural(toContact, 'student')} to contact</span>` : ''}
      </div>
      ${[...upcoming, ...past].map(a => absenceHtml(a, today)).join('')}`;
    el.hidden = false;
  }

  function absenceHtml(a, today) {
    const when = a.date === today ? 'Today' : a.date < today ? 'Past' : '';
    const facts = [plural(a.removedSlots, 'time') + ' removed', plural(a.cancelledCount, 'appointment') + ' cancelled'];
    if (a.keptCheckedIn) facts.push(`${a.keptCheckedIn} already checked in`);
    const rows = a.students.map(s => `
      <tr>
        <td>${HC.esc(HC.slotRange(s.timeSlot))}</td>
        <td>${HC.esc(s.fullName)} <span class="muted">${HC.esc(s.studentId)}</span></td>
        <td>${s.phoneNumber ? HC.esc(s.phoneNumber) : '<span class="muted">Not provided</span>'}</td>
        <td>${s.email ? HC.esc(s.email) : '<span class="muted">Not provided</span>'}</td>
        <td>
          <label class="absence-tick"><input type="checkbox" ${s.contacted ? 'checked' : ''} onchange="DoctorAbsences.setContacted(${s.id}, this.checked)"> Contacted</label>
          ${s.contacted ? `<div class="tiny muted">${HC.esc(s.contactedBy)} · ${HC.esc(HC.timeOf(s.contactedAt))}</div>` : ''}
        </td>
      </tr>`).join('');
    return `
      <div class="absence-item${when === 'Past' ? ' past' : ''}">
        <div class="pill-row">
          <strong>${HC.esc(doctorLabel(a.doctorName))} · ${HC.esc(HC.fmtDate(a.date))}</strong>
          ${when ? `<span class="pill${when === 'Today' ? ' red' : ''}">${when}</span>` : ''}
        </div>
        <p class="tiny muted" style="margin-top:4px;">${HC.esc(facts.join(' · '))} · Marked by ${HC.esc(a.markedBy)}, ${HC.esc(HC.fmtDateTime(a.markedAt))}</p>
        ${a.students.length
          ? `<div class="table-wrap" style="margin-top:10px;"><table class="table">
              <thead><tr><th>Time</th><th>Student</th><th>Phone</th><th>Email</th><th></th></tr></thead>
              <tbody>${rows}</tbody>
            </table></div>`
          : '<p class="small muted" style="margin-top:8px;">No students had booked.</p>'}
      </div>`;
  }

  function setContacted(id, contacted) {
    fetch(`/absences/students/${id}/contacted`, {
      method: 'POST',
      headers: { 'Authorization': opts.authHeader(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ contacted })
    })
      .then(res => res.ok ? res.json() : Promise.reject())
      .then(updated => {
        absences.forEach(a => a.students.forEach((s, i) => { if (s.id === updated.id) a.students[i] = updated; }));
        render();
      })
      .catch(() => load()); // put the tick back to what the server has
  }

  return { mount, load, setContacted };
})();
