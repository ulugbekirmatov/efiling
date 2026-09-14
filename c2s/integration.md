# How C2S reaches the outside world

C2S backend Nashorn has no working outbound HTTP client in the source repo. The steps below send email, run a scheduled job, and split long work into chunks. The first and last sections explain the HTTP gap and Java interop.

## About where an outbound HTTP call can originate

This section is explanation. Opinion stays here.

`iwb.request` and `fetch` run in the browser. Every `iwb.request` site in the source repo targets a platform ajax endpoint. The URLs that appear are `ajaxQueryData?_qid=`, `ajaxExecDbFunc?_did=`, and `ajaxPostForm?a=&_fid=`. No call passes an external `http://` or `https://` URL. Call sites pass `url`, `params`, `successCallback`, `errorCallback`, and sometimes `requestWaitMsg: true` (`calendarjs/form.js:1567`). No site sets `method`, `headers`, or `timeout`. Root `CLAUDE.md` labels `iwb.request` as a backend HTTP helper. That label does not match the call sites.

```js browser
iwb.request({
    url: "ajaxExecDbFunc?_did=4185",
    params: { onepay_employee_payroll_id : onepayEmployeePayrollId },
    successCallback: function (resp) {
        var data = resp && resp.result && resp.result.result ? resp.result.result : resp && resp.result ? resp.result : resp;
        if (!data || !data.success) { _this.setState({ isLoading: false }); return; }
    },
    errorCallback: function (err) {
        console.error('Request failed:', err);
        _this.setState({ isLoading: false });
    }
});
```
Source: claude-projects/south_carolina/payroll_run.js:140

A few pages POST JSON with browser `fetch`. Those calls hit a platform URL such as `showForm?a=2&_fid=9494`, with `credentials: "same-origin"`.

```js browser
const doRequest = fetch(url, {
    body: JSON.stringify(params || {}),
    cache: "no-cache",
    credentials: "same-origin",
    headers: { "content-type": "application/json" },
    method: "POST",
    mode: "cors",
    redirect: "follow",
    referrer: "no-referrer"
});
```
Source: claude-projects/calendarjs/page.js:1476

No backend Nashorn file makes an outbound HTTP call. Searches for `$.http`, `$.rest`, `$.fetch`, `$.webRequest`, `java.net.HttpURLConnection`, and `java.net.http.HttpClient` in non-worktree `.js` files returned no hits.

Two routes could reach the MeF service. Neither is proven.

**Browser page calls the MeF REST API.** The page would use `iwb.request` or `fetch` against the MeF host instead of a platform ajax URL. The one design note that proposes an external HTTP call is Phase 7 of `dmv/net-pay-dotnet-migration-plan.html`. That plan proposes replacing `$.execFunc(funcId, ...)` inside `did_3944.js` with an `iwb.request` call. `did_3944.js` is a Nashorn DID, so the plan assumes `iwb.request` exists in Nashorn. Nothing in the source repo shows that it does. The plan does not show a URL, headers, or auth. Same-origin credentials on the calendar `fetch` do not transfer to a different host. Cross-origin behavior of a C2S-rendered page calling Spring Boot is not observed in the source repo.

**Nashorn uses `Java.type` for `java.net.HttpURLConnection`.** `Java.type` is available. The only use in the source repo is `Java.type("iwb.exception.IWBException")`. An HTTP client built that way would be new. Gotchas for sockets, TLS, redirects, and timeouts are not observed in the source repo.

The gap is real. Picking a route now would hide the missing auth and session work.

### Open decision

A human must settle these questions before any MeF call is written.

- May a C2S page call a different origin, or does the platform block that?
- How does the MeF REST API authenticate a C2S caller?
- Where does the IRS MeF session live, in the browser, in Nashorn, or only inside the Spring Boot service?

Do not pick a route until those are answered.

## Send email from a backend function

Use `$.sendFormSmsMail(templateId, options)` to notify people. It does not call the MeF service. It sends mail through the platform's template system.

1. Ask the platform owner for a template ID. The ID is assigned by the platform and is immutable. Do not guess one. Live IDs in the source repo include 948, 946, and 876.
2. Build an options object. Reserved keys are `to`, `cc`, `subject`, and `message`. Every other key becomes a template variable.
3. Gate the send behind the file's `DEBUG_MODE` or `DRY_RUN` write helper.
4. Call `$.sendFormSmsMail` with the template ID and the options object.

Template HTML reads those extra keys as `${req.FIELD_NAME}`.

```html
<p>A Bonus Assignment for <strong>${req.EMPLOYEE_NAME}</strong> was approved in the <strong>C2S system</strong>.</p>
<p><strong>Company Code:</strong> ${req.COMPANY_CODE}</p>
<p><strong>Pay Date:</strong> ${req.PAY_DATE}</p>
```
Source: claude-projects/bonus_app_c2s/bonus_approved_email.html:264

```js
var DEBUG_MODE = true;
var DRY_RUN = true;
var SUPPRESS = DRY_RUN || DEBUG_MODE;
function write(tag, payload, fire) {
    $.console((SUPPRESS ? 'suppressed ' : 'applied ') + payload, tag);
    if (!SUPPRESS) fire();
}
var mailFields = { to: 'payroll@onewell.org', cc: ccList, subject: 'New Bonus Approved - ' + employeeName,
    EMPLOYEE_NAME: employeeName, COMPANY_CODE: companyCode, PAY_DATE: payDate };
write('SENDMAIL-948', 'pay_date=' + payDate, function () { $.sendFormSmsMail(948, mailFields); });
```
Source: claude-projects/bonus_app_c2s/on_bonus_approval_workflow.js:10

A template can also take a raw `message` string instead of field keys. Template 876 does that.

```js
$.sendFormSmsMail(876, {
    to: 'payroll@onewell.org' ,
    subject: 'Employees First Time In Payroll ! ',
    message: mes,
    cc: 'umit.bakir@onewell.org' ,
});
```
Source: claude-projects/south_carolina/fnc_generate_non_exempt_payroll_NJ_GD0_V2.js:6934

## Run a job on a schedule

`holiday_bonus_generate.js` runs nightly on the C2S scheduler. Registration is done in the C2S admin UI, not in code. No file in the source repo shows a schedule ID or a registration API. The function the job runs has a platform-assigned DID. A human obtains that ID from the platform owner. Never guess it.

Make the job idempotent with a SQL existence check before any write. The holiday generator loads every `onepay_bonus_assignment` under the rule's `bonus_rule_id`, then skips an employee who already has a row.

```js
var existingRows = $.sqlQuery(
    "SELECT a.employee_id"
    + " FROM onepay_bonus_assignment a"
    + " JOIN onepay_bonus_definition d ON d.bonus_definition_id = a.bonus_definition_id"
    + " WHERE d.bonus_rule_id = " + (1 * rule.bonus_rule_id)
) || [];
var existingSet = {};
var xi;
for (xi = 0; xi < existingRows.length; xi++) {
    existingSet[existingRows[xi].employee_id] = true;
}
```
Source: claude-projects/bonus_app_c2s/holiday_bonus_generate.js:410

```js
for (i = 0; i < employees.length; i++) {
    var empId = employees[i].employee_id;
    var dedupKey = empId;
    if (existingSet[dedupKey]) { skipped++; continue; }
}
```
Source: claude-projects/bonus_app_c2s/holiday_bonus_generate.js:476

1. Query the rows that would make a second run a duplicate.
2. Skip those keys before `$.postForm` or `$.sqlExecute`.
3. Register the function in the C2S admin UI and use the DID the platform assigns.
4. Gate writes with `DEBUG_MODE` or `DRY_RUN`. The holiday generator also accepts `xdry_run`.

There is no distributed lock and no scheduler config file in the source repo.

## Split long work into chunks

Copy `aca/redesign/batch_runners/aca_batch_runner_skeleton.js`. One DID is the chunk worker. A dashboard button fires that DID many times at once. Concurrency comes from the browser, not from Nashorn.

1. Ask the platform owner for a new DID for the worker. Do not invent one. Live IDs include 4548 for the ACA part-time batch and 4316 for the engine the older-hire skeleton calls.
2. Load one ordered id list. The cohort query must end with `ORDER BY employee_id`.
3. Keep a disjoint slice with stride. Chunk `n` of `N` processes index `i` when `(i % N) === n`.
4. From a dashboard button, loop `xchunk_index` from 0 to `N - 1` and fire one `iwb.request` per index against the same DID.

```js
var chunkIndex = parseInt(req.xchunk_index, 10);
var chunkTotal = parseInt(req.xchunk_total, 10);
var i;
for (i = 0; i < ids.length; i++) {
  if ((i % chunkTotal) !== chunkIndex) continue;
}
```
Source: claude-projects/.claude/skills/aca-batch-runner/SKILL.md:28

The skeleton uses file-scope `CHUNK_INDEX` and `CHUNK_TOTAL` instead of `req`, and skips with `(i % CHUNK_TOTAL) !== CHUNK_INDEX`. Match whichever style the runner you copy already uses.

```js browser
var i;
for (i = 0; i < RUN_CHUNK_TOTAL; i++) {
  (function (chunkIndex) {
    iwb.request({
      url: 'ajaxExecDbFunc?_did=' + FNID_BATCH_RUNNER,
      params: { xemployee_ids: idsCsv, xchunk_index: chunkIndex, xrun_mode: 'SCHEDULED' },
      successCallback: function () { onChunkDone(true); },
      errorCallback: function () { onChunkDone(false); }
    });
  })(i);
}
```
Source: claude-projects/aca/redesign/aca_missing_eval_finder_page.js:510

End the worker with `var result = ...;` and `JSON.stringify` any object you return.

```js
var result = JSON.stringify(summary);
```
Source: claude-projects/bonus_app_c2s/holiday_bonus_generate.js:543

## About Java interop from Nashorn

This section is explanation. Opinion stays here.

`Java.type` is available. The source repo uses it only for `iwb.exception.IWBException`. No `java.util`, `java.time`, or networking type is loaded. Building an MeF HTTP or XML payload with Java types would be new ground.

```js
var msg;
try {
    var IWBException = Java.type("iwb.exception.IWBException");
    if (e instanceof IWBException) {
        msg = e.getStack()[0].getMessage() + "";
    } else {
        msg = (e && e.message) ? String(e.message) : String(e);
    }
} catch (inner) {
    msg = (e && e.message) ? String(e.message) : String(e);
}
```
Source: claude-projects/south_carolina/fnc_payroll_dispatcher.js:93

JDBC values come back as Java host objects. `typeof` of `java.lang.Boolean`, `java.lang.String`, and `java.math.BigDecimal` is `object`. `typeof x === 'number'` and `typeof x === 'string'` are false for a `$.sqlQuery` cell. `String(x)` or `Number(x)` comes before `JSON.stringify`. A top-level object assigned to the function output serializes as Java `Map.toString()`, with `=` instead of `:`, unless the backend runs `JSON.stringify` first.

```js
function bool(s) {
    var t2 = String(s).toLowerCase().replace(/^\s+|\s+$/g, '');
    return t2 === "true" || t2 === "t" || t2 === "1" || t2 === "yes";
}
```
Source: claude-projects/dmv/dmv_payroll.js:2413
