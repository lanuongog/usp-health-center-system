// Form helper: a short guide under every text box, live "what's wrong" messages,
// the S11 student ID format, and the automatic @student.usp.ac.fj email suggestion.
// Loaded on every page with forms. It never blocks a request itself except the
// login screen, where it stops an ID that is not in the S11 + 6 digits format.
(function () {
  'use strict';

  var STUDENT_ID = /^S11\d{6}$/;
  var EMAIL_OK = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
  var EMAIL_DOMAIN = '@student.usp.ac.fj';
  var ID_MSG = 'Student ID must be S11 followed by 6 digits, for example S11222222.';
  var ID_HINT = 'Student ID: S11 followed by 6 digits, for example S11222222.';

  function $(id) { return document.getElementById(id); }
  function val(id) { var e = $(id); return e ? e.value : ''; }

  function phoneCheck(v) {
    v = v.trim();
    if (!v) return '';
    var digits = v.replace(/\D/g, '');
    if (!/^\+?[0-9 ()-]+$/.test(v) || digits.length < 7 || digits.length > 15) {
      return 'Enter a valid phone number: 7 to 15 digits, for example +679 912 3456.';
    }
    return '';
  }
  function nameCheck(v) {
    v = v.trim();
    if (!v) return '';
    if (!/^[\p{L}][\p{L} .'’-]*$/u.test(v)) return 'Use letters, spaces, hyphens and apostrophes only.';
    return '';
  }
  function lengthCheck(min, max) {
    return function (v) {
      if (!v) return '';
      if (v.length < min) return 'Must be at least ' + min + ' characters.';
      if (v.length > max) return 'Must be ' + max + ' characters or fewer.';
      return '';
    };
  }
  function studentIdCheck(v) {
    v = v.trim();
    if (!v) return '';
    return STUDENT_ID.test(v.toUpperCase()) ? '' : ID_MSG;
  }
  function numberRange(min, max, whole, unit) {
    return function (v) {
      v = v.trim();
      if (!v) return '';
      var re = whole ? /^\d+$/ : /^\d+(\.\d+)?$/;
      if (!re.test(v)) return 'Enter ' + (whole ? 'a whole number' : 'a number') + ' from ' + min + ' to ' + max + unit + '.';
      var n = parseFloat(v);
      if (n < min || n > max) return 'Enter a value from ' + min + ' to ' + max + unit + '.';
      return '';
    };
  }

  // id -> { hint, check(value, el), count: { max, unit } }
  var RULES = {
    // sign in and registration
    loginUsername: { hint: 'Students: S11 followed by 6 digits (e.g. S11222222). Staff: your staff username.',
      check: function (v) { v = v.trim(); return (/^s\d/i.test(v) && !STUDENT_ID.test(v.toUpperCase())) ? ID_MSG : ''; } },
    loginPassword: { hint: 'Enter the password for your account.' },
    regGivenName: { hint: 'Letters only, up to 50 characters.', check: nameCheck },
    regFamilyName: { hint: 'Letters only, up to 49 characters.', check: nameCheck },
    regCountry: { hint: 'Choose your home country. It decides your Health Services Fee.' },
    regReceipt: { hint: 'PDF file only, up to 5 MB.',
      check: function (v, el) {
        var f = el.files && el.files[0];
        if (!f) return '';
        if (!/\.pdf$/i.test(f.name) && f.type !== 'application/pdf') return 'The receipt must be a PDF file.';
        if (f.size > 5 * 1024 * 1024) return 'The receipt must be 5 MB or smaller.';
        return '';
      } },
    regPhone: { hint: 'Include the country code, 7 to 15 digits, for example +679 912 3456.', check: phoneCheck },
    regEmail: { hint: 'Use your USP student email, for example s11222222@student.usp.ac.fj.',
      check: function (v) { v = v.trim(); return (v && !EMAIL_OK.test(v)) ? 'Enter a valid email address, for example s11222222@student.usp.ac.fj.' : ''; } },
    regSemesterAddress: { hint: 'Hall, room and campus. Up to 200 characters and 30 words.', count: { max: 200, unit: 'characters' } },
    regHomeAddress: { hint: 'Village or town and island. Up to 200 characters and 30 words.', count: { max: 200, unit: 'characters' } },
    regEmergencyName: { hint: 'Full name of your emergency contact, up to 100 characters.', check: nameCheck },
    regEmergencyRelationship: { hint: 'For example Mother, Father or Guardian. Up to 50 characters.' },
    regEmergencyPhone: { hint: '7 to 15 digits. It must be different from your own phone number.',
      check: function (v) {
        var e = phoneCheck(v);
        if (e) return e;
        var mine = val('regPhone').replace(/\D/g, '');
        if (v.trim() && mine && v.replace(/\D/g, '') === mine) return 'The emergency contact’s number must be different from your own.';
        return '';
      } },
    regStudentId: { hint: ID_HINT, check: studentIdCheck },
    regPassword: { hint: '6 to 100 characters.', check: lengthCheck(6, 100) },
    regPasswordConfirm: { hint: 'Type the same password again.',
      check: function (v) { return (v && v !== val('regPassword')) ? 'The passwords do not match.' : ''; } },

    // change password (every role)
    cpCurrentPassword: { hint: 'Your current password.' },
    cpNewPassword: { hint: '6 to 100 characters, and different from your current password.',
      check: function (v) {
        var e = lengthCheck(6, 100)(v);
        if (e) return e;
        return (v && v === val('cpCurrentPassword')) ? 'The new password must be different from the current one.' : '';
      } },
    cpConfirmPassword: { hint: 'Type the new password again.',
      check: function (v) { return (v && v !== val('cpNewPassword')) ? 'The passwords do not match.' : ''; } },

    // admin
    resetPw: { hint: '6 to 100 characters.', check: lengthCheck(6, 100) },
    rejectReason: { hint: 'Shown to the student. Up to 200 characters.', count: { max: 200, unit: 'characters' } },
    userSearchInput: { hint: 'Type part of an account ID or name to filter the list.' },
    newUsername: { hint: '3 to 50 characters.', check: lengthCheck(3, 50) },
    newPassword: { hint: '6 to 100 characters.', check: lengthCheck(6, 100) },
    feeLocal: { hint: 'Amount in FJ$, from 0 to 10,000.', check: numberRange(0, 10000, false, '') },
    feeRegional: { hint: 'Amount in FJ$, from 0 to 10,000.', check: numberRange(0, 10000, false, '') },
    feeReason: { hint: 'Required. Say why the fee is changing. Up to 500 characters.', count: { max: 500, unit: 'characters' } },
    rejectNote: { hint: 'Required when you reject. Explain what to change. Up to 500 characters.', count: { max: 500, unit: 'characters' } },

    // nurse
    queueSearch: { hint: 'Type part of a name or student ID to filter the list.' },
    recordsSearchInput: { hint: 'Type part of a name or student ID to filter the list.' },
    directorySearchInput: { hint: 'Type part of a name or student ID to filter the list.' },
    'records-search': { hint: 'Type part of a name or student ID to filter the list.' },
    'directory-search': { hint: 'Type part of a name or student ID to filter the list.' },
    temperature: { hint: 'A number from 30 to 43 °C, for example 36.7.', check: numberRange(30, 43, false, ' °C') },
    bpSystolic: { hint: 'Whole number from 60 to 250 mmHg.', check: numberRange(60, 250, true, ' mmHg') },
    bpDiastolic: { hint: 'Whole number from 40 to 150 mmHg, lower than the systolic value.',
      check: function (v) {
        var e = numberRange(40, 150, true, ' mmHg')(v);
        if (e) return e;
        var s = parseInt(val('bpSystolic'), 10), d = parseInt(v, 10);
        return (v && !isNaN(s) && d >= s) ? 'Diastolic must be lower than systolic.' : '';
      } },
    presentingComplaint: { hint: 'Why the student is here, up to 500 characters.', count: { max: 500, unit: 'characters' } },

    // emergency (nurse and doctor)
    quickEmergencyId: { hint: ID_HINT, check: studentIdCheck },
    emergencyStudentId: { hint: ID_HINT, check: studentIdCheck },
    emergencyReason: { hint: 'Optional. What is happening, up to 300 characters.', count: { max: 300, unit: 'characters' } },

    // doctor and schedule
    'diagnosis-input': { hint: 'Required. Up to 200 characters.', count: { max: 200, unit: 'characters' } },
    'notes-input': { hint: 'Required. Up to 2000 characters.', count: { max: 2000, unit: 'characters' } },
    newDoctorName: { hint: 'Doctor username, for example dr.patel.' }
  };

  function fire(el, type) { el.dispatchEvent(new Event(type, { bubbles: true })); }

  function attach(el) {
    var rule = RULES[el.id];
    if (!rule || el.dataset.fhDone) return;
    // leave fields that already have their own counter or error line
    var d = el.getAttribute('aria-describedby');
    if (d && d.split(/\s+/).some(function (x) { return x && document.getElementById(x); })) return;
    el.dataset.fhDone = '1';

    var host = el.closest('.field') || el.parentNode;
    var existing = host.querySelector(':scope > .help');
    var hint = null, anchor = el;
    if (existing) {
      anchor = existing;
    } else {
      hint = document.createElement('p');
      hint.className = 'help fh-hint';
      hint.id = el.id + '-fh-hint';
      hint.appendChild(document.createTextNode(rule.hint));
      el.insertAdjacentElement('afterend', hint);
      anchor = hint;
    }
    var err = document.createElement('p');
    err.className = 'error-text fh-error';
    err.id = el.id + '-fh-error';
    err.setAttribute('role', 'alert');
    anchor.insertAdjacentElement('afterend', err);
    el.setAttribute('aria-describedby', (hint ? hint.id + ' ' : '') + err.id);

    var counter = null;
    if (rule.count && hint) {
      counter = document.createElement('span');
      counter.className = 'fh-count';
      hint.appendChild(counter);
    }

    function run(showEmpty) {
      var v = el.type === 'file' ? '' : el.value;
      var msg = rule.check ? (rule.check(v, el) || '') : '';
      err.textContent = msg;
      el.classList.toggle('fh-invalid', !!msg);
      if (counter) {
        counter.textContent = ' ' + v.length + ' / ' + rule.count.max + ' ' + rule.count.unit;
        counter.classList.toggle('fh-over', v.length > rule.count.max);
      }
      return msg;
    }
    el.addEventListener('input', function () { run(false); });
    el.addEventListener('change', function () { run(false); });
    el.addEventListener('blur', function () { run(true); });
    run(false);
  }

  // Email suggestion: once a valid student ID is typed, offer s11xxxxxx@student.usp.ac.fj.
  function setupEmail() {
    var idEl = $('regStudentId'), em = $('regEmail');
    if (!idEl || !em || em.dataset.fhEmail) return;
    em.dataset.fhEmail = '1';
    var host = em.closest('.field') || em.parentNode;
    var chip = document.createElement('button');
    chip.type = 'button';
    chip.className = 'fh-suggest';
    chip.hidden = true;
    host.appendChild(chip);

    function suggestion() {
      var v = idEl.value.trim().toUpperCase();
      return STUDENT_ID.test(v) ? v.toLowerCase() + EMAIL_DOMAIN : '';
    }
    function refresh() {
      var s = suggestion();
      if (s && !em.value.trim()) {
        chip.textContent = 'Use ' + s + ' (click or press Enter)';
        chip.hidden = false;
      } else {
        chip.hidden = true;
      }
    }
    function accept() {
      var s = suggestion();
      if (!s) return false;
      em.value = s;
      fire(em, 'input');
      chip.hidden = true;
      return true;
    }
    chip.addEventListener('click', function () { accept(); em.focus(); });
    idEl.addEventListener('input', refresh);
    em.addEventListener('input', refresh);
    em.addEventListener('focus', refresh);
    em.addEventListener('keydown', function (e) {
      if (e.key !== 'Enter') return;
      var v = em.value.trim();
      if (!v && suggestion()) { e.preventDefault(); accept(); }
      else if (v && v.indexOf('@') === -1) { e.preventDefault(); em.value = v.toLowerCase() + EMAIL_DOMAIN; fire(em, 'input'); }
    });
    em.addEventListener('blur', function () {
      var v = em.value.trim();
      if (v && v.indexOf('@') === -1 && /^s11\d{6}$/i.test(v)) { em.value = v.toLowerCase() + EMAIL_DOMAIN; fire(em, 'input'); }
    });
    refresh();
  }

  // Sign in: a student ID that is not S11 + 6 digits is stopped before it is sent.
  function setupLogin() {
    var form = $('loginForm');
    if (!form || form.dataset.fhLogin) return;
    form.dataset.fhLogin = '1';
    form.addEventListener('submit', function (e) {
      var u = val('loginUsername').trim();
      if (/^s\d/i.test(u) && !STUDENT_ID.test(u.toUpperCase())) {
        e.preventDefault();
        e.stopImmediatePropagation();
        var err = $('loginUsername-fh-error');
        if (err) err.textContent = ID_MSG;
        $('loginUsername').classList.add('fh-invalid');
        $('loginUsername').focus();
      }
    }, true);
  }

  function scan() {
    Object.keys(RULES).forEach(function (id) {
      var el = $(id);
      if (el) attach(el);
    });
    setupEmail();
    setupLogin();
  }

  var queued = false;
  function queueScan() {
    if (queued) return;
    queued = true;
    requestAnimationFrame(function () { queued = false; scan(); });
  }
  function start() {
    scan();
    new MutationObserver(queueScan).observe(document.body, { childList: true, subtree: true });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
