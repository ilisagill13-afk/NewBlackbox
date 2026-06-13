// Injected into the authenticated ais.usvisa-info.com page. All requests run same-origin
// (credentials:'include') so the session cookie + CSRF apply automatically. Each method posts
// its result back to the native side via AndroidBridge.onResult(reqId, jsonString).
(function () {
  if (window.VisaBot) return;

  function origin() { return location.origin; }

  function csrfToken() {
    var m = document.querySelector('meta[name="csrf-token"]');
    return m ? m.getAttribute('content') : '';
  }

  function report(reqId, obj) {
    try { AndroidBridge.onResult(reqId, JSON.stringify(obj)); } catch (e) {}
  }

  window.VisaBot = {
    isLoggedIn: function (reqId) {
      report(reqId, { ok: true, loggedIn: location.pathname.indexOf('/users/sign_in') === -1 });
    },

    getDays: function (reqId, pathPrefix, scheduleId, facilityId) {
      var url = origin() + pathPrefix + '/schedule/' + scheduleId +
        '/appointment/days/' + facilityId + '.json?appointments[expedite]=false';
      fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'include' })
        .then(function (r) {
          if (r.url && r.url.indexOf('/users/sign_in') !== -1) return Promise.reject('SESSION_EXPIRED');
          if (!r.ok) return Promise.reject('HTTP ' + r.status);
          return r.text();
        })
        .then(function (t) { report(reqId, { ok: true, data: t }); })
        .catch(function (e) { report(reqId, { ok: false, error: String(e) }); });
    },

    getTimes: function (reqId, pathPrefix, scheduleId, facilityId, date) {
      var url = origin() + pathPrefix + '/schedule/' + scheduleId +
        '/appointment/times/' + facilityId + '.json?date=' + date + '&appointments[expedite]=false';
      fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'include' })
        .then(function (r) {
          if (r.url && r.url.indexOf('/users/sign_in') !== -1) return Promise.reject('SESSION_EXPIRED');
          if (!r.ok) return Promise.reject('HTTP ' + r.status);
          return r.text();
        })
        .then(function (t) { report(reqId, { ok: true, data: t }); })
        .catch(function (e) { report(reqId, { ok: false, error: String(e) }); });
    },

    // body = already-url-encoded appointment fields (without authenticity_token).
    book: function (reqId, pathPrefix, scheduleId, body) {
      var token = csrfToken();
      var url = origin() + pathPrefix + '/schedule/' + scheduleId + '/appointment';
      fetch(url, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'X-CSRF-Token': token
        },
        body: 'authenticity_token=' + encodeURIComponent(token) + '&' + body
      })
        .then(function (r) {
          report(reqId, { ok: (r.ok || r.redirected), status: r.status, url: r.url });
        })
        .catch(function (e) { report(reqId, { ok: false, error: String(e) }); });
    }
  };
})();
