# How to call C2S functions and queries from a page

Call a C2S backend function, query, or form from the page script with `iwb.request` and `iwb.openForm`. Do not invent a function ID, a query ID, or a form ID. The platform assigns those numbers and they do not change.

Page scripts are browser JavaScript. They are not limited to Nashorn ES5. Live payroll pages still use `var` and `function` callbacks. Follow that style when you add calls.

## Structure the page script

The page file is the component tree. End the file by returning the root element. Pull CDN libraries with `XLazyScriptLoader`, then render the page component. In the real file the `return` sits at the tail. The wrapper below is only so the snippet parses here.

```js browser
(function () {
  return React.createElement(
    XLazyScriptLoader,
    {
      loadjs: ['https://unpkg.com/gridjs/dist/gridjs.umd.js'],
      loadcss: ['https://unpkg.com/gridjs/dist/theme/mermaid.min.css'],
      loading: React.createElement('div', null, 'Loading…')
    },
    React.createElement(RunPayrollPage, {})
  );
})();
```
Source: claude-projects/payroll_run_page/run_payroll.js:9706

Register the live instance with `props.setCmp(this)`. Seed `state` from `iwb.forms`, keyed by the generated form or page id. Fetch in `componentDidMount`. Other entry points are click handlers such as the dashboard re-run button.

```js browser
function Page(props) {
  if (props.setCmp) props.setCmp(this);
  this.egrids = {};
  this.state = (!props.values && iwb.forms['fi_1771384055033']) || {
    errors: {},
    ai_components: {},
    values: props.values || { xparticipant_ids: '', xemployee_ids: '' }
  };
}
```
Source: claude-projects/calendarjs/form.js:22

Read a form field from `this.state.values`. Write it with `setState`. Read page URL parameters from `_request`. After teardown, delete the cache entry so a later open of the same id does not show stale values.

```js browser
function cleanupFormInstance(formInstance) {
  if (!formInstance) return;
  if (formInstance._id && iwb.forms && iwb.forms[formInstance._id]) {
    delete iwb.forms[formInstance._id];
  }
}
```
Source: claude-projects/intake/intake_form_v1.js:185

The page has these globals. None of them are imported.

- `iwb` is the client API.
- `_request` holds page URL parameters such as `_request.onepay_employee_payroll_id`.
- `_scd` is the session (`employeeId`, `roleId`, `user`). Payroll pages gate actions on `_scd.employeeId`.
- `_` is `React.createElement`.
- `toastr` shows success and error toasts.
- `window.confirm` gates destructive bulk actions.

## Call a backend function by DID

Use `iwb.request` with `url`, `params`, `successCallback`, and `errorCallback`. Set `url` to `ajaxExecDbFunc?_did=` plus the numeric DID. Pass the parameter names the function already reads. Some functions use an `x` prefix. DID 4185 uses `onepay_employee_payroll_id` with no prefix.

The callback argument is a platform envelope, not the backend `result` value. Unwrap `resp.result.result`, then `resp.result`, then `resp`. Some functions put the payload on `resp.result.data`. Match the output variable of that DID.

A backend `result` that is a bare object arrives as Java `toString`. Functions that return objects assign `result = JSON.stringify(...)`. If the unwrapped value is a string, parse it.

```js browser
var DID_EVAL_LIST = 4637;
function loadEvalList(employeeId, month, year) {
  var _this = this;
  this.setState({ isLoading: true, error: '', records: [], allRecords: [] });
  iwb.request({
    url: 'ajaxExecDbFunc?_did=' + DID_EVAL_LIST,
    params: {
      xemployee_id: employeeId || 0,
      xeval_display_dt: this._buildEvalDt(month, year),
      xeval_source_dashboard: 1
    },
    successCallback: function (resp) {
      var raw =
        (resp && resp.result && resp.result.result != null) ? resp.result.result :
        (resp && resp.result != null) ? resp.result :
        resp;
      var data;
      try {
        data = (typeof raw === 'string') ? JSON.parse(raw) : raw;
      } catch (e) {
        _this.setState({ isLoading: false, error: 'Failed to parse evaluation data.' });
        return;
      }
      if (!data || typeof data.length !== 'number') data = [];
      _this.setState({ records: data, allRecords: data, isLoading: false });
    },
    errorCallback: function () {
      _this.setState({ isLoading: false, error: 'Failed to load evaluation data.' });
    }
  });
}
```
Source: claude-projects/aca/redesign/eval_dashboard_page.js:363

Wrong. Do not treat `resp` as the payload. The platform wraps the backend `result`.

```js nocheck
iwb.request({
  successCallback: function (resp) {
    this.setState({ records: resp });
  }
});
```

## Run a query by QID

Use the same `iwb.request` shape. Set `url` to `ajaxQueryData?_qid=` plus the numeric QID. Put bind variables in `params`. Query 15417 binds `xonepay_payroll_id`.

The query result is `resp.data`, an array of row objects whose keys are the SELECT column names. Map those rows into component state. After `setState`, build the gridjs table from `this.state.detailRows` in `_initDetailGrid`. That call is in `payroll_run_page/run_payroll.js` at line 2320.

```js browser
function loadEarnings() {
  var self = this;
  this.setState({ isLoadingEarnings: true });
  iwb.request({
    url: 'ajaxQueryData?_qid=15417',
    params: { xonepay_payroll_id: Number(this.state.payrollId) },
    successCallback: function (resp) {
      var rows = (resp && resp.data) ? resp.data : [];
      var mapped = (rows || []).map(function (r) {
        return {
          employee_earning_id: String(r.employee_earning_id || ''),
          employee_id: r.employee_id,
          employee_name: String(r.employee_name || ''),
          amount: parseFloat(r.amount || 0),
          hours: parseFloat(r.hours || 0),
          earning_type_id: String(r.earning_type_id || '')
        };
      });
      self.setState({ earnRows: mapped, isLoadingEarnings: false });
    },
    errorCallback: function (err) {
      console.error(err);
      toastr.error('Failed to load earnings.');
      self.setState({ isLoadingEarnings: false });
    }
  });
}
```
Source: claude-projects/payroll_run_page/new_design_payroll_run.js:229

The other URL form inlines the QID and binds with `iwb.JSON2URI`. Append `.r=` plus `Math.random()` so the GET is not cached.

```js browser
function loadDsps(xparticipant_ids) {
  iwb.request({
    url: 'ajaxQueryData?' + iwb.JSON2URI({
      _qid: 13924,
      xdsps_for_participant_ids: xparticipant_ids
    }) + '.r=' + Math.random(),
    successCallback: function (resp) { var rows = resp.data; }
  });
}
```
Source: claude-projects/calendarjs/form.js:453

## Submit a form

`$.postForm` is backend only. Do not call it from the page.

To open the platform form UI, call `iwb.openForm` with a `showForm` URL. `a=2` is insert. `_fid` is the form ID. Parameters that start with `x` prefill fields. Pass `{ modal: true, modalSize: 'lg' }` to open a modal instead of navigating away.

```js browser
function openEvalForm(minEvalDt) {
  iwb.openForm(
    'showForm?a=2&_fid=15927' + (minEvalDt ? '&xeval_dt=' + minEvalDt : ''),
    { modal: true, modalSize: 'lg' }
  );
}
```
Source: claude-projects/aca/redesign/eval_dashboard_page.js:729

To post without opening that UI, call `iwb.request` against `ajaxPostForm`. `a=1` is edit. `a=3` is delete. Put the primary key on the URL. Check `res.success`.

```js browser
function updateAgenda(event) {
  iwb.request({
    url: 'ajaxPostForm?a=1&_fid=9494&tagenda_id=' + event.id,
    params: ui2Servercenverter(event),
    successCallback: function (res) {
      if (!res.success) toastr.error('ajaxPostForm?a=1&_fid=9494&tagenda_id=');
    },
    errorCallback: function (err) { console.error(err); }
  });
}
```
Source: claude-projects/calendarjs/page.js:1511

Some pages POST the same `ajaxPostForm` URL with `fetch`.

```js browser
function deleteByPk(formID, pkFieldName, pkValue) {
  var deleteUrl = 'ajaxPostForm?a=3&_fid=' + formID +
    '&t' + pkFieldName + '=' + encodeURIComponent(pkValue);
  fetch(deleteUrl, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    credentials: 'same-origin',
    cache: 'no-cache'
  });
}
```
Source: claude-projects/intake/qcss_form.js:785

Close a modal or tab with `iwb.closeModal()` or `iwb.closeTab()`. To persist through a backend function instead, call the DID that itself calls `$.postForm`.

## Handle errors and show them to the user

Always pass `errorCallback`. Log with `console.error`. Show the user a `toastr.error`. Pages do not call backend `$.console(message, title)`.

`successCallback` means the HTTP call completed. It does not mean the backend operation succeeded. Check the success flag the function actually returns. DID 4185 uses `data.success`. If the flag is missing or false, show `data.message` and stop.

```js browser
function sendToBank(sendFuncId, payrollId) {
  iwb.request({
    url: 'ajaxExecDbFunc?_did=' + sendFuncId,
    params: { xpayroll_id: Number(payrollId) },
    successCallback: function (resp) {
      var data = (resp && resp.result && resp.result.result)
        ? resp.result.result : resp;
      if (data && data.success) {
        toastr.success(data.message || 'Sent to bank.');
      } else {
        toastr.error((data && data.message) || 'Send to bank failed.');
      }
    },
    errorCallback: function (err) {
      console.error(err);
      toastr.error('Send to bank request failed.');
    }
  });
}
```
Source: claude-projects/payroll_run_page/run_payroll.js:2526

Set a loading flag true before the call. Clear it in both callbacks and in the `JSON.parse` catch. If parse fails, log `console.error('Parse X failed:', e)` and restore fallback state so the UI does not hang.

The fields on the `errorCallback` argument are not observed in the source repo. Pages pass that argument to `console.error` and do not read named properties off it.

## Fire work in parallel or in sequence

When each call is independent, fire them together. The missing-eval dashboard button confirms first. Then it starts `RUN_CHUNK_TOTAL` DID calls at once. `RUN_CHUNK_TOTAL` is 10. Capture the loop index in a function you call with the current `i`. Count completions in a shared `onChunkDone`. Refresh once when `doneCount` reaches the total.

`RUN_CHUNK_TOTAL` must equal `CHUNK_TOTAL` in the paired backend runner. The runner splits the id list into that many chunks.

```js browser
var FNID_BATCH_RUNNER = 4631;
var RUN_CHUNK_TOTAL = 10;
function rerunChunks(idsCsv) {
  var _this = this;
  var doneCount = 0;
  var failedCount = 0;
  if (!window.confirm('Re-run the ACA engine for these employees?')) return;
  this.setState({ isRunning: true });
  function onChunkDone(ok) {
    doneCount += 1;
    if (!ok) failedCount += 1;
    if (doneCount < RUN_CHUNK_TOTAL) return;
    _this.setState({ isRunning: false, runIsError: failedCount > 0 });
    _this._loadData();
  }
  var i;
  for (i = 0; i < RUN_CHUNK_TOTAL; i++) {
    (function (chunkIndex) {
      iwb.request({
        url: 'ajaxExecDbFunc?_did=' + FNID_BATCH_RUNNER,
        params: {
          xemployee_ids: idsCsv,
          xchunk_index: chunkIndex,
          xrun_mode: 'SCHEDULED'
        },
        successCallback: function () { onChunkDone(true); },
        errorCallback: function () { onChunkDone(false); }
      });
    })(i);
  }
}
```
Source: claude-projects/aca/redesign/aca_missing_eval_finder_page.js:504

When call N+1 needs call N's rows, nest the next `iwb.request` inside `successCallback`. Calendar load of DSPs, then procedure codes, then care plans is that chain.

```js browser
function loadDependentQueries(xparticipant_ids) {
  iwb.request({
    url: 'ajaxQueryData?' + iwb.JSON2URI({
      _qid: 13924, xdsps_for_participant_ids: xparticipant_ids
    }) + '.r=' + Math.random(),
    successCallback: function (resp) {
      iwb.request({
        url: 'ajaxQueryData?' + iwb.JSON2URI({
          _qid: 14836, xparticipant_ids: xparticipant_ids
        }) + '.r=' + Math.random(),
        successCallback: function (resp2) {
          var procedureCodes = resp2.data;
        }
      });
    }
  });
}
```
Source: claude-projects/calendarjs/form.js:453

## Two canonical call shapes

DID call. Unwrap the envelope. Parse if the payload is still a string.

```js browser
function callDid(did, params) {
  iwb.request({
    url: 'ajaxExecDbFunc?_did=' + did,
    params: params,
    successCallback: function (resp) {
      var raw =
        (resp && resp.result && resp.result.result != null) ? resp.result.result :
        (resp && resp.result != null) ? resp.result : resp;
      var data = (typeof raw === 'string') ? JSON.parse(raw) : raw;
    },
    errorCallback: function (err) { console.error(err); toastr.error('Request failed.'); }
  });
}
```
Source: claude-projects/aca/redesign/eval_dashboard_page.js:366

QID call. Read `resp.data` as the row array.

```js browser
function callQid(qid, params) {
  iwb.request({
    url: 'ajaxQueryData?_qid=' + qid,
    params: params,
    successCallback: function (resp) {
      var rows = (resp && resp.data) ? resp.data : [];
    },
    errorCallback: function (err) { console.error(err); }
  });
}
```
Source: claude-projects/payroll_run_page/new_design_payroll_run.js:229
