# C2S platform rules and gotchas

Facts the Nashorn backend, the SQL validators, and the Node harness enforce. The last section is the only one with commands.

## Nashorn ES5 constraints

The PostToolUse hook `dmv/.claude/hooks/validate-nashorn-es5.sh` scans added lines in `*.js` files. It strips `//` comments and JSDoc continuations, then blocks these tokens.

| Token | Regex | Label |
| --- | --- | --- |
| `const` | `\bconst\b` | `const` |
| `let` | `\blet\b` | `let` |
| arrow function | `=>` | `arrow_function` |
| template literal | backtick | `template_literal` |

The hook does not scan `class`, destructuring, `Array.prototype.includes`, `Object.assign`, for-of, default params, or spread. The source repo still avoids those by convention. Test files self-report that hygiene. `Object.keys` is ES5.1 and is used.

| Feature | ES5 replacement |
| --- | --- |
| `const`, `let` | `var` |
| arrow function | `function` |
| template literal | string concatenation with `+` |
| `class` | constructor `function` or object literal |
| destructuring | named property reads |
| `Array.prototype.includes` | `indexOf(x) > -1` |
| `Object.assign` | a `for` copy of own keys |
| for-of | indexed `for` |
| default params | `typeof x === 'undefined'` then assign |
| spread | `concat` or a push loop |

Nashorn has no `require` and no module system. Shared logic is copy-pasted between a Node-testable oracle file and the live engine file.

## Runtime quirks

`$.sqlQuery` cells are Java host objects. `typeof` of `java.lang.Boolean`, `java.lang.String`, and `java.math.BigDecimal` is `object`. `bool()` in the payroll engines normalizes through `String()`.

```js
function bool(s) {
  if (s === null || s === undefined) return false;
  if (typeof s === "boolean") return s;
  if (typeof s === "number") return s !== 0;
  if (typeof s === "string") {
    var t = s.toLowerCase().replace(/^\s+|\s+$/g, '');
    return t === "true" || t === "t" || t === "1" || t === "yes";
  }
  var t2 = String(s).toLowerCase().replace(/^\s+|\s+$/g, '');
  return t2 === "true" || t2 === "t" || t2 === "1" || t2 === "yes";
}
```
Source: claude-projects/dmv/dmv_payroll.js:2413

A numeric SQL column can still arrive as a string. `state_territory` comes back as `"54"` for VA, not `"VA"`.

The source repo disagrees on what `$.sqlQuery` returns when no row matches. A dmv review states `null` (`dmv/reviews/payroll-timesheets-dmv-20260429T160453Z.md:48`). An adversary review traced a live bug to an empty array, which is truthy (`south_carolina/ADVERSARY-REVIEW-2026-08-25.md:188`). A bare `if (rows)` does not catch the empty array. The intended throw is skipped and `rows[0]` raises a Nashorn TypeError. Guard both with `!rows || !rows.length`.

Wrong.

```js nocheck
if (resOfInfoQuery) {
    payrollStartDate = resOfInfoQuery[0].sta_dt;
}
```
Source: claude-projects/south_carolina/sc_payroll_timesheets.js:507

```js
function scEmployeeExists(employeeId) {
    var rows = $.sqlQuery(
        "SELECT employee_id FROM hr_employee WHERE employee_id = ${req.employee_id} LIMIT 1",
        { employee_id: employeeId }
    );
    return rows && rows.length > 0 && (rows[0].employee_id * 1) > 0;
}
```
Source: claude-projects/south_carolina/sc_payroll_timesheets.js:382

A top-level object output from a `_did` script serializes as Java `Map.toString()`, for example `{data=[...], success=true}`, unless the backend assigns `JSON.stringify(...)`. Whether the name `result` is required by the platform is not fully confirmed in the source repo. Callers that need JSON run `JSON.stringify` on the backend and `JSON.parse` on the page.

```js
var xjobValue = (typeof xjob !== 'undefined') ? xjob : null;
var options = payDateOptions(xemployee_id, xjobValue);
var result = (xjobValue === 1 || xjobValue === '1') ? JSON.stringify(options) : options;
```
Source: claude-projects/bonus_app_c2s/available_pay_dates.js:237

Backend functions end with `var result = ...;` and no trailing bare `result;` expression.

`$.console` is a host method with no `.apply`. Proxies dispatch on `arguments.length`.

```js
function scDbg(a, b) {
  if (!SC_DEBUG_MODE) return;
  if (typeof $ === 'undefined' || !$ || !$.console) return;
  if (arguments.length <= 1) {
    $.console(a);
  } else {
    $.console(a, b);
  }
}
```
Source: claude-projects/south_carolina/sc_payroll.js:160

JDBC date cells are Java host objects like the other JDBC types. Coerce with `String(...)` before `JSON.stringify`.

`setSCDebugSink` is an optional function pointer. Unset, `scDbg` talks to `$.console`. Set, only in the Node harness, it routes to a stepping sink.

## Platform semantics

The platform owns the transaction for every script invocation. Backend JS does not emit `BEGIN`, `COMMIT`, `ROLLBACK`, `SAVEPOINT`, or `START TRANSACTION`. Manual control corrupts session state. The hook `validate-no-tx-control.sh` blocks those tokens.

Query IDs, function IDs, form IDs, paygroup IDs, and template IDs are assigned by the platform and are immutable. Code never guesses a number. Placeholders stay `null` or `TODO` until the platform owner issues the value. Primary keys use `NEXTVAL` on an existing sequence, not `MAX(id)+1`.

Request fields have no fixed runtime type. Call sites see numbers and strings for the same field and compare with `=== 1 || === '1'`. Callers coerce with `1 *` or `parseInt(..., 10)` before numeric use.

`${req.X}` and `${_scd.X}` in `$.sqlQuery` and `$.sqlExecute` are literal string substitution, not bound parameters. String, date, and timestamp params are quoted. Numeric params are left unquoted.

```sql
SELECT TO_CHAR('${req.xnext}'::date, 'MM/DD/YYYY') AS next_display
FROM onepay_bonus_assignment ba
WHERE ba.bonus_assignment_id = ${req.xid}
```
Source: claude-projects/bonus_app_c2s/on_bonus_approval_workflow.js:23

The hook `validate-sql-conventions.sh` requires the placeholder shape `${req.field}` or `${_scd.field}` on `sqlQuery` and `sqlExecute` lines. Bare `${var}` is blocked. The hook does not check quote escaping. An unescaped string such as `O'Brien` in `'${req.employee_name}'` can break the SQL. A sibling insert escapes quotes with `.replace(/'/g, "''")`.

```js
var noteSql = "'" + String(note).replace(/'/g, "''") + "'";
```
Source: claude-projects/south_carolina/sc_payroll_timesheets.js:368

`_scd` is the session object. Documented keys are `employee_id`, `roleId`, and `user`. Table names in SQL carry no `public.` prefix. Some old files qualify a table with a tenant schema (`south_carolina/sc_payroll_timesheets.js:394`). Do not copy that. `hr_company` joins on `code`, not `adp_company_id`.

## Logging and PII

`$.console(message, title)` puts the tag in the title argument. It does not put a `[TAG]` prefix in the message.

```js
$.console('assignment=' + assignmentId + ' employee_id=' + employeeId + ' approval_dt=' + payDate, 'HEADER');
```
Source: claude-projects/bonus_app_c2s/on_bonus_approval_workflow.js:34

Wrong.

```js nocheck
scDbg("[FED-DBG] loaded w4 from onepay_employee_w4: " + JSON.stringify(w4), "calculateFederalTax");
```
Source: claude-projects/south_carolina/sc_payroll.js:1222

Employee names and SSNs do not enter logs or Claude context. Spreadsheets are read through `south_carolina/tools/read_xlsx.js` or `analyze_sc_workbooks.js`, which hash PII into tokens such as `SSN:a1b2c3d4`. Scripts do not log `clientOpts`, `password`, `user`, or full `process.env`. Debug instrumentation logs write payloads and counts, not a full eval result object.

`DEBUG_MODE` is per project. dmv defaults `DMV_DEBUG_MODE` to `true` and `DMV_DRY_RUN` to `false`, so it logs and it writes. The ACA mobile backend keeps `DEBUG_MODE` false because its debug line logs the full eval result, which is PII.

```js
var DEBUG_MODE = false;
```
Source: claude-projects/aca/onepay_aca_measurement_eval_mobile_backend.js:17

```js
var result = findCurrentStatusGeneratingEval(xemployee_id);
if (DEBUG_MODE) {
    $.console(result, '[ACA Current Status Eval] result=');
}
result = JSON.stringify(result)
```
Source: claude-projects/aca/onepay_aca_measurement_eval_mobile_backend.js:782

## Secrets

Secrets are not hardcoded in C2S backend JS. `.env` is never read, grepped, or opened with `Read`, `cat`, `head`, `tail`, or `grep`. Local Node DB scripts construct `new Client()` with no args, or from `process.env.DATABASE_URL`. The process env is filled by Node's `--env-file` flag. `pg_query_worker.js` never reads `.env` itself.

```js
'use strict';
var Client = require('pg').Client;
function buildClientOpts() {
  var base = {};
  if (process.env.DATABASE_URL) {
    var u = new URL(process.env.DATABASE_URL);
    base.host = u.hostname;
    base.user = decodeURIComponent(u.username);
    base.password = decodeURIComponent(u.password);
    return base;
    // the real function also sets port, database, ssl, and search_path
  }
  return base;
}
```
Source: claude-projects/dmv/tools/pg_query_worker.js:38

Launch shape, from `run_safe_query.js`.

```sh
node --env-file=dmv/.env dmv/tools/run_safe_query.js
```
Source: claude-projects/dmv/tools/run_safe_query.js:3

## Local testing

Production Nashorn files add a `module.exports` guard that is inert on the platform, where `module` is undefined. Node tests `require` the file.

```js
if (typeof module !== 'undefined' && module && module.exports) {
  module.exports = {
    num: num,
    cents: cents,
    setStateIdMap: setStateIdMap,
    setDMVDebugMode: setDMVDebugMode
  };
}
```
Source: claude-projects/dmv/dmv_payroll.js:5299

The harness installs a fake `global.$`. `$.sqlQuery` routes by a table-name substring in the SQL text. `$.console` lines are captured. Test files sit under the same ES5 hook, so they keep forbidden tokens out of their own source. A test that must match a backtick builds it with `String.fromCharCode(96)`.

```js
'use strict';
function installFakeDollar(rowsByTable) {
  var consoleLines = [];
  var queries = [];
  var fake = {
    sqlQuery: function (sql) {
      queries.push(sql);
      var s = String(sql);
      if (s.indexOf('onepay_employee_md_mw507') > -1) {
        return (rowsByTable && rowsByTable.mw507) || [];
      }
      if (s.indexOf('onepay_employee_va_va4') > -1) {
        return (rowsByTable && rowsByTable.va4) || [];
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
  if (typeof global.$ !== 'undefined') {
    try { delete global.$; } catch (e) { global.$ = undefined; }
  }
}
```
Source: claude-projects/dmv/tests/dmv_payroll_db_test.js:60

Front-line syntax gate is `node --check` on the backend file. debug-trace instrumentation is one pass over the whole failing data path. Flags at the top of the file are `DEBUG_MODE`, `DRY_RUN`, and `DEBUG_EMPLOYEE_ID`. The debug-trace scaffold puts each `$.postForm` and `$.sqlExecute` behind `if (DEBUG_MODE)` for the duration of the trace. Production files keep `DEBUG_MODE` for logs and `DRY_RUN` for writes. Cleanup strips scaffold `$.console` calls, resets `DEBUG_EMPLOYEE_ID` to `null`, and leaves the write gate in place.

## Run the checks

The ES5 hook has no `--file` flag. It reads PostToolUse JSON on stdin. `CLAUDE_PROJECT_DIR` is the claude-projects root.

```sh
printf '%s\n' '{"tool_name":"Edit","tool_input":{"file_path":"dmv/dmv_payroll.js","new_string":"var x = 1;"}}' | CLAUDE_PROJECT_DIR=/Users/ulugbekirmatov/Documents/claude-projects bash /Users/ulugbekirmatov/Documents/claude-projects/dmv/.claude/hooks/validate-nashorn-es5.sh
```
The hook reads stdin at claude-projects/dmv/.claude/hooks/validate-nashorn-es5.sh:7. The command above is an invocation example, not a line from the repo.

The JSON keys the hook reads are `tool_name` (`Edit`, `Write`, or `MultiEdit`) and a path from `tool_input.file_path`, `tool_input.path`, `tool_input.target_file`, `tool_response.filePath`, or `tool_response.file_path`. Added text comes from `git diff` when the file is tracked, else from `tool_input.new_string`, `tool_input.edits[].new_string`, or `tool_input.content`.

This repo checks every fenced JavaScript block in `c2s/*.md`.

```sh
bash c2s/tools/check-snippets.sh
```
The script lives in this repo at `c2s/tools/check-snippets.sh`.
