// Shared rendering for Health Services Fee changes, used by the admin Fees
// page (propose / withdraw) and the fee approver page (approve / reject).
// A fee change only takes effect once a fee approver approves it.
const FeeChanges = (() => {
  function money(n) { return 'FJ$' + Number(n).toFixed(2); }

  // "FJ$50.00 → FJ$60.00", or just the amount when it isn't changing.
  function amountChange(from, to) {
    return Number(from) === Number(to) ? HC.esc(money(to))
      : `<span class="muted">${HC.esc(money(from))}</span> → <strong>${HC.esc(money(to))}</strong>`;
  }

  function changeRows(r) {
    return `
      <div class="kv"><span>Local students (Fiji)</span><div>${amountChange(r.previousLocalFee, r.proposedLocalFee)}</div></div>
      <div class="kv"><span>Regional students</span><div>${amountChange(r.previousRegionalFee, r.proposedRegionalFee)}</div></div>`;
  }

  function statusPill(status) {
    const [cls, label] = {
      PENDING: ['amber', 'Waiting for approval'], APPROVED: ['green', 'Approved'],
      REJECTED: ['red', 'Rejected'], WITHDRAWN: ['', 'Withdrawn']
    }[status] || ['', status];
    return `<span class="pill ${cls}">${HC.esc(label)}</span>`;
  }

  function currentHtml(f) {
    const changed = f.updatedAt
      ? `Last changed ${HC.esc(HC.fmtDateTime(f.updatedAt))} · proposed by ${HC.esc(f.updatedBy)}`
        + (f.approvedBy ? `, approved by ${HC.esc(f.approvedBy)}` : '')
      : 'Default fees — never changed.';
    return `
      <div class="kv"><span>Local students (Fiji)</span><strong>${HC.esc(money(f.localFee))}</strong></div>
      <div class="kv"><span>Regional students (other USP countries)</span><strong>${HC.esc(money(f.regionalFee))}</strong></div>
      <p class="tiny muted" style="margin-top:10px;">${changed}</p>`;
  }

  // Past proposals (the pending one is shown on its own).
  function historyHtml(list) {
    const done = list.filter(r => r.status !== 'PENDING');
    if (!done.length) return '<p class="small muted" style="margin-top:12px;">No fee changes yet.</p>';
    return `<div class="table-wrap" style="margin-top:14px;"><table class="table">
      <thead><tr><th>Proposed</th><th>Local</th><th>Regional</th><th>Reason</th><th>Outcome</th></tr></thead>
      <tbody>${done.map(r => `<tr>
        <td>${HC.esc(HC.fmtDateTime(r.proposedAt))}<div class="tiny muted">by ${HC.esc(r.proposedBy)}</div></td>
        <td>${amountChange(r.previousLocalFee, r.proposedLocalFee)}</td>
        <td>${amountChange(r.previousRegionalFee, r.proposedRegionalFee)}</td>
        <td class="small">${HC.esc(r.reason)}</td>
        <td>${statusPill(r.status)}<div class="tiny muted">${HC.esc(r.decidedBy || '')}${r.decidedAt ? ' · ' + HC.esc(HC.fmtDateTime(r.decidedAt)) : ''}</div>
          ${r.decisionNote ? `<div class="tiny">${HC.esc(r.decisionNote)}</div>` : ''}</td>
      </tr>`).join('')}</tbody>
    </table></div>`;
  }

  return { money, changeRows, statusPill, currentHtml, historyHtml };
})();
