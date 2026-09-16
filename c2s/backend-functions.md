# How to write a C2S backend function

Write an ES5 file the C2S platform runs in Nashorn. A page calls it at `ajaxExecDbFunc?_did=N`. You write the file. The platform assigns the DID. You never guess that number.

## Treat the file as a DID the platform owns

A DID is a platform-assigned function ID. It is an immutable integer. The page passes it as the `_did` query parameter. `_did` is not a Nashorn output variable.

Name the local file so a reader can find it.

- `did_<N>.js` puts the function ID in the filename. `dmv/payroll_run_page/did_3896.js` is function ID 3896.
- `fnc_*.js` names a backend function without encoding the ID. `south_carolina/fnc_net_pay_nj.js` is this shape.
- `on_*.js` names an event or workflow file. `bonus_app_c2s/on_bonus_approval_workflow.js` is this shape.

Copy every ID from the platform owner or from an existing registry. Record IDs in a table in the project's `CLAUDE.md`. The DMV tree also stores them in `.claude/policy/immutable-id-registry.txt` as `KEY=$.postForm(<N>,` tokens. If the ID is not issued yet, leave a `null` or `TODO` placeholder. Do not invent a number.

Two invocation shapes exist. DID scripts run top-level and read fields from `_request`. Library files such as `dmv/dmv_payroll.js` define functions, then self-invoke only when platform globals (`$`, `xemployee_id`) are present. Node can then load the same file.

## Lay out the file, then end on result

Use banner comments for config constants, helpers, main work, and the result line. `dmv/dmv_payroll.js` labels `SECTION 1: Coercion helpers` and later the debug flags. Put `DEBUG_MODE` and `DRY_RUN` near the top.

End the file with `var result = ...;` and nothing after it. Do not add a trailing bare `result;` expression. DID scripts in the source repo often assign to a platform-predeclared `result` without `var`. Use `var result = ...;` in files you write.

The next example reads one request field, runs one query, and returns a JSON string. It is a shortened rewrite of `did_3896.js`, which returns a nested `employeeInfo` and `payrollInfo` body.

```js
var onepayEmployeePayrollId = _request.onepay_employee_payroll_id;
var masterSql =
  "SELECT ep.employee_id, e.first_name, e.last_name " +
  "FROM onepay_employee_payroll ep " +
  "JOIN hr_employee e ON e.employee_id = ep.employee_id " +
  "WHERE ep.employee_payroll_id = " + onepayEmployeePayrollId;
var master = $.sqlQuery(masterSql);
var payload;
if (!master || master.length === 0) {
  payload = {
    success: false,
    message: "No payroll row found for ID " + onepayEmployeePayrollId
  };
} else {
  payload = {
    success: true,
    employee_id: master[0].employee_id,
    name: master[0].first_name + " " + master[0].last_name
  };
}
var result = JSON.stringify(payload);
```

Source: claude-projects/dmv/payroll_run_page/did_3896.js:1

On validation failure, assign `result` and `return` so the rest of the file does not run.

```js
if (!onepayEmployeePayrollId) {
  result = JSON.stringify({
    success: false,
    message: "Param 'onepay_employee_payroll_id' is required."
  });
  return;
}
```

Source: claude-projects/dmv/payroll_run_page/did_3896.js:6

## Read `_request` and `_scd`

Read caller inputs from `_request`. Field names match the platform form or function parameters. Some names carry an `x` prefix (`_request.xonepay_payroll_id`, `_request.xemployee_id`). Some do not (`_request.onepay_employee_payroll_id`, `_request.company_code`). Use the names the function definition already declares.

Do not assume the runtime type of an incoming value. Call sites treat the same field as a number or a string. Coerce before arithmetic with `1 * _request._tb_pk` or `_scd.roleId * 1`. Compare with `xjob === 1 || xjob === '1'`, not a bare `if (value)`.

When the platform fires `dmv/dmv_payroll.js` as funcId 4452, it passes per-employee fields as bare globals (`xemployee_payroll_id`, `xemployee_id`, `xonepay_payroll_id`) instead of `_request`. Check `typeof xemployee_id !== 'undefined'` before you read them.

Use `_scd` for identity and role gating. Do not use it as a data source. Call sites read `employeeId`, `roleId`, `userId`, `employee_id`, and a nested `user` object. Root docs list `employee_id`, `roleId`, and `user`. Read the shape the file already uses. Do not invent a new one.

```js
if (_scd.employeeId != 5732 && _scd.employeeId != 2205 &&
    _scd.employeeId != 1601 && _scd.employeeId != 9850) {
  throw new Error("You do not have access to run additional payroll.");
}
```

Source: claude-projects/dmv/payroll_run_page/did_3931.js:5

## Query with `$.sqlQuery`

Call `$.sqlQuery(sqlString)`. It returns an array of row objects. The source repo disagrees on what comes back when no row matches. A dmv review states the platform returns `null` (claude-projects/dmv/reviews/payroll-timesheets-dmv-20260429T160453Z.md:48). An adversary review traced a live bug to an empty array, which is truthy (claude-projects/south_carolina/ADVERSARY-REVIEW-2026-08-25.md:188). Guard both with `!rows || !rows.length` before you index. Column keys are lowercase. Values arrive as strings or Java host objects. `typeof` reports `"object"` for boxed JDBC values. Coerce with `Number()`, `parseFloat()`, or `String()` before you compute or compare.

An empty array is truthy, so a bare `if (rows)` still enters the branch and then throws when you read `rows[0]`.

This pattern is wrong.

```js nocheck
if (resOfInfoQuery) {
    payrollStartDate = resOfInfoQuery[0].sta_dt;
    payrollEndDate   = resOfInfoQuery[0].end_dt;
}
```

Source: claude-projects/south_carolina/sc_payroll_timesheets.js:507

Guard length instead.

```js
var master = $.sqlQuery(masterSql);
if (!master || master.length === 0) {
  result = JSON.stringify({
    success: false,
    message: "No payroll row found for ID " + onepayEmployeePayrollId
  });
  return;
}
master = master[0];
```

Source: claude-projects/dmv/payroll_run_page/did_3896.js:24

Iterate with an indexed `for`. Coerce numeric columns as you copy each row.

```js
var jsEarn = [];
for (var k = 0; rawEarn && k < rawEarn.length; k++) {
    var e = rawEarn[k];
    jsEarn.push({
        hours: e.hours !== null ? parseFloat(e.hours) : null,
        amount: e.amount !== null ? parseFloat(e.amount) : 0
    });
}
```

Source: claude-projects/dmv/payroll_run_page/did_3896.js:266

`${req.X}` inside a normal quoted SQL string is platform string substitution, not a bound parameter. Quote string, date, and timestamp placeholders. Leave numeric placeholders bare.

```js
var rollSql = "SELECT TO_CHAR('${req.xnext}'::date, 'MM/DD/YYYY') AS next_display " +
  "FROM onepay_bonus_assignment ba WHERE ba.bonus_assignment_id = ${req.xid}";
```

Source: claude-projects/bonus_app_c2s/on_bonus_approval_workflow.js:23

Do not write a template literal. The backtick is not ES5. Put `${req.X}` inside a normal quoted string. Hand-concatenated SQL follows the same quoting. Numerics stay bare. Dates are written as `DATE '` plus the value plus `'`. For the full quoting rules, see [How to write C2S queries and inline SQL](queries.md).

## Write with `$.sqlExecute` or `$.postForm`

Use `$.sqlExecute(sqlString)` for INSERT, UPDATE, or DELETE SQL that does not go through a form.

```js
var updateSQL = "UPDATE onepay_employee_payroll " +
  "SET net_pay = " + netPay + ", " +
  "    gross_pay = " + cents(grossPay) + " " +
  "WHERE employee_payroll_id = " + employeePayrollId;
$.sqlExecute(updateSQL);
```

Source: claude-projects/dmv/dmv_payroll.js:5849

Use `$.postForm(formId, action, fields)` when a registered form owns the write. Action `2` inserts. Action `1` edits. The form ID is a platform integer. Copy it from the registry. Never guess it.

```js
$.postForm(15703, 1, {
  temployee_payroll_id: _request.xemployee_payroll_id,
  net_pay: allPay,
  gross_pay: allPay
});
```

Source: claude-projects/south_carolina/fnc_net_pay_nj.js:30

```js
$.postForm(13214, 2, payload);
```

Source: claude-projects/south_carolina/sc_payroll_timesheets.js:312

New primary keys come from `NEXTVAL`, never from `MAX(id)+1`.

```sql
NEXTVAL('seq_onepay_employee_tax_payment')
```

Source: claude-projects/south_carolina/sc_payroll.js:4082

The platform owns the transaction for every script invocation. Do not emit transaction-control SQL.

## Gate every write

Keep two flags. `DEBUG_MODE` gates logging. `DRY_RUN` gates writes. Files also name them `DMV_DEBUG_MODE` and `DMV_DRY_RUN`, or `SC_DEBUG_MODE` and `SC_DRY_RUN`. The two flags are independent. A dry run logs the payload and skips `$.postForm` and `$.sqlExecute`. It also skips pre-insert DELETEs so existing rows stay intact.

`dmv/dmv_payroll.js` defaults `DMV_DEBUG_MODE` to `true` and `DMV_DRY_RUN` to `false`. `south_carolina/sc_payroll.js` uses the same defaults. Set `DRY_RUN` to `false` when the function must write. Set `DEBUG_MODE` to `false` when the function must not log. The ACA mobile backend keeps `DEBUG_MODE` false because its debug line logs PII.

Recheck the dry-run flag immediately before every platform write.

```js
var DMV_DEBUG_MODE = true;
var DMV_DRY_RUN = false;
```

Source: claude-projects/dmv/dmv_payroll.js:99

```js
if (DMV_DRY_RUN) {
  dmvDbg("dmv_payroll: DRY RUN skip employee_payroll update sql=" + updateSQL, "dmv_dry_run");
  return;
}
$.sqlExecute(updateSQL);
```

Source: claude-projects/dmv/dmv_payroll.js:99

`bonus_app_c2s/on_bonus_approval_workflow.js` folds both flags into one `SUPPRESS` chokepoint. `bonus_app_c2s/bonus_eval.js` uses `DEBUG_MODE` alone as a preview switch. When `DEBUG_MODE` is true, `createAssignment` logs and skips `$.postForm`.

```js
var DEBUG_MODE = true;
var DRY_RUN = true;
var DEBUG_BONUS_ASSIGNMENT_ID = 108;
var assignmentId = 1 * (_request._tb_pk == null || _request._tb_pk === '' ? DEBUG_BONUS_ASSIGNMENT_ID : _request._tb_pk);
var SUPPRESS = DRY_RUN || DEBUG_MODE || (DEBUG_BONUS_ASSIGNMENT_ID !== null && assignmentId !== 1 * DEBUG_BONUS_ASSIGNMENT_ID);
function write(tag, payload, fire) {
  $.console((SUPPRESS ? "suppressed " : "applied ") + payload, tag);
  if (!SUPPRESS) fire();
}
```

Source: claude-projects/bonus_app_c2s/on_bonus_approval_workflow.js:2

## Log a message and a title

Call `$.console(message, title)`. Put the tag in the title argument. Do not prefix the message with `[TAG]`.

This call folds the tag into the message. That is wrong.

```js nocheck
$.console("Step 1: W-4 loaded | " + JSON.stringify(payload));
```

Source: claude-projects/dmv/payroll_run_page/did_4020.js:321

Put the tag in the second argument.

```js
$.console("assignment=" + assignmentId + " employee_id=" + employeeId + " approval_dt=" + payDate, "HEADER");
```

Source: claude-projects/bonus_app_c2s/on_bonus_approval_workflow.js:34

Nashorn's `$.console` is a host method with no `.apply`. Dispatch by `arguments.length` in a small proxy if you gate logs behind `DEBUG_MODE`.

```js
function dmvDbg(a, b) {
  if (!DMV_DEBUG_MODE) return;
  if (typeof $ === "undefined" || !$ || !$.console) return;
  if (arguments.length <= 1) {
    $.console(a);
  } else {
    $.console(a, b);
  }
}
```

Source: claude-projects/dmv/dmv_payroll.js:135

Log IDs, counts, and short status tags. Do not log names, emails, SSNs, or a full `$.sqlQuery` result array. Older DID files dump raw row arrays with a single-arg `$.console(rawEarn)`. Do not copy that.

## Return JSON in `result`

The output variable is `result`. Assign a JSON string when the payload is an object. A bare object comes back to the page as Java `toString()`, shaped like `{data=[...], success=true}` with equals signs instead of JSON colons.

```js
result = JSON.stringify(response);
```

Source: claude-projects/dmv/payroll_run_page/did_3896.js:299

`response` in that file carries `success: true` plus the payload. Failure paths use `success: false` and `message`. On the page, unwrap `resp.result.result`. If the value is a string, call `JSON.parse`. Treat `success === true` or `success === "true"` as success, because the flag can arrive as a string.

```js browser
iwb.request({
  url: "ajaxExecDbFunc?_did=3896",
  params: { onepay_employee_payroll_id: onepayEmployeePayrollId },
  successCallback: function (resp) {
    var data = (resp && resp.result && resp.result.result) ? resp.result.result
      : (resp && resp.result) ? resp.result
      : resp;
    var ok = data && (data.success === true || data.success === "true" || data.status === "OK");
    if (!ok) {
      return;
    }
  }
});
```

Source: claude-projects/south_carolina/payroll_run.js:2868

For DID 4565 the `ajaxExecDbFunc` response is `{ success, db_func_id }` with no function payload. Where the platform decides whether a function's `result` reaches the page is not observed in the source repo.

## Test under Node with a fake `$`

Nashorn has no `require`. Node has no `$` object. Add an export guard so Node can load a library file. Nashorn has no `module` global, so the guard is a no-op there. DID scripts that run top-level are not observed with this guard.

```js
if (typeof module !== "undefined" && module && module.exports) {
  module.exports = { num: num };
}
```

Source: claude-projects/dmv/dmv_payroll.js:5299

Install a fake `global.$` that records `sqlQuery` text and `console` lines. Match SQL with `indexOf` on a distinctive table name. Return canned rows. Tear the fake down after the assertion.

```js
"use strict";
var assert = require("assert");
function installFakeDollar(rowsByTable) {
  var consoleLines = [];
  var queries = [];
  var fake = {
    sqlQuery: function (sql) {
      queries.push(sql);
      var s = String(sql);
      if (s.indexOf("onepay_employee_md_mw507") > -1) {
        return (rowsByTable && rowsByTable.mw507) || [];
      }
      return [];
    },
    console: function (msg) {
      consoleLines.push(String(msg));
    }
  };
  global.$ = fake;
  return { consoleLines: consoleLines, queries: queries };
}
function clearFakeDollar() {
  if (typeof global.$ !== "undefined") {
    try { delete global.$; } catch (e) { global.$ = undefined; }
  }
}
var probe = installFakeDollar({ mw507: [{ employee_id: "7" }] });
assert.ok(probe.queries);
clearFakeDollar();
```

Source: claude-projects/dmv/tests/dmv_payroll_db_test.js:60

Sibling tests add `sqlExecute`, `postForm`, and `execFunc` when the file under test calls those methods. Run syntax first, then the test file.

```sh
node --check dmv/dmv_payroll.js
node tests/dmv_payroll_test.js
```

The test command is at claude-projects/dmv/tests/dmv_payroll_test.js:3. The `node --check` habit is stated at claude-projects/south_carolina/CLAUDE.md:297.
