# How to write C2S queries and inline SQL

Call a registered SELECT from a page with its QID. Write INSERT, UPDATE, and DELETE inline with `$.sqlExecute`.

## Call a registered query by QID

A QID is a SELECT stored in the C2S query editor. The platform assigns the number when you paste the SQL. Never invent that number.

Put the assigned value in a page constant. Leave a TODO on the constant until the editor returns a real ID.

```js browser
var RATE_QID = 16523;   // <-- TODO: set to the query created from sql/employee_rate_query.sql
iwb.request({
  url: 'ajaxQueryData?_qid=' + RATE_QID,
  params: {
    xemployee_id: empId,
    xonepay_payroll_id: this.state.earningsPayrollId
  }
});
```
Source: claude-projects/south_carolina/payroll_run.js:6658

Some constants are numeric strings. Copy the surrounding file.

```js browser
var DD_ACH_LIST_QID = '16552';
iwb.request({
  url: 'ajaxQueryData?_qid=' + DD_ACH_LIST_QID,
  params: { xpayroll_id: pid },
  successCallback: function (resp) {
    var achRows = (resp && resp.data) || [];
  }
});
```
Source: claude-projects/south_carolina/payroll_run.js:3523

`params` keys become `${req.<key>}` in the stored SQL. The page above passes `xpayroll_id`. The query uses `${req.xpayroll_id}`.

A second observed call style puts those keys on the URL.

```js browser
iwb.request({
  url: 'ajaxQueryData?_qid=9370&xSevenDaysBeforeMonth=' + fmtDateTime(sevenDaysBeforeMonth) + '&xSevenDaysAfterMonth=' + fmtDateTime(sevenDaysAfterMonth)
});
```
Source: claude-projects/calendarjs/page.js:1460

If you are invoking a backend function instead of a query, call `ajaxExecDbFunc?_did=N`. Record that DID in the same ID table as the QIDs.

### Keep a local SQL mirror and an ID table

After the platform assigns `N`, save the stored SQL as `qid_<N>.sql` next to the page. `dmv/payroll_run_page/` keeps `qid_15222.sql`, `qid_15218.sql`, `qid_15768.sql`, and the other registered payroll-run queries that way.

Before registration, keep a draft as `sql/<name>.sql` with no number. `OTHER_INFO_QID` is 16520. Its comment names `sql/other_info_query.sql` as the SQL that was pasted to create it.

Copy the table in `dmv/payroll_run_page/payroll_run_page_ids.md` into this repo when you add MeF IDs. That file maps each QID and DID to a purpose and call-site line. Its header states that every ID is immutable and platform-assigned.

## Write the SELECT the platform will store

Paste SELECT-only SQL into the query editor. Writes do not go through a QID.

Verify every column against `shared/ddl/*.sql` before you name it. Do not copy a column name from neighboring JS.

Use lowercase aliases. Row objects keep those names with no camelCase rewrite. Dropdown queries return exactly two columns aliased `id` and `dsc`.

Keep the `/*!*/` markers the editor template puts on `FROM`, `WHERE`, `GROUP BY`, and `ORDER BY`.

```sql
SELECT
x.first_name ||' '|| x.last_name dsc, employee_id id

/*!*/FROM
hr_employee x

/*!*/WHERE


/*!*/GROUP BY


/*!*/ORDER BY
x.first_name ||' '|| x.last_name
```
Source: claude-projects/dmv/payroll_run_page/qid_15222.sql:1

QID 15222's mirror leaves `WHERE` empty. The page that calls it still passes `xcompany_code`.

## Quote request parameters as string substitution

`${req.X}` is plain string substitution. It is not a bound parameter.

Wrap string, date, and timestamp values in single quotes. Cast dates and timestamps. Leave numeric values bare.

```js
var sql = "WHERE x.hire_dt >= '${req.start_date}'::date "
  + "  AND x.hire_dt <= '${req.end_date}'::date "
  + "  AND x.lkp_work_assignment_status = 1 "
  + "  AND x.company_id = ${req.company_id}";
```
Source: claude-projects/minimum_wage_table/pa_new_hire_report.js:178

```sql
WHERE hw.employee_id = ${req.xemployee_id}
```
Source: claude-projects/bonus_app_c2s/pay_dates.sql:25

```sql
CASE WHEN '${req.xsorting_field1}' = 'company_code' THEN p.company_code END
```
Source: claude-projects/payroll_history/payroll_history_query.sql:362

The following pattern is wrong. The date has no quotes. The numeric id is quoted.

```js nocheck
var sql = "WHERE x.hire_dt >= ${req.start_date}::date "
  + "  AND hw.employee_id = '${req.xemployee_id}'";
```
The rule is stated at claude-projects/bonus_app_c2s/available_pay_dates_test.js:9.

On a `$.sqlQuery` or `$.sqlExecute` line, placeholders must be `${req.field}` or `${_scd.field}`. Pass the values through the second argument so the keys match `req`.

```js
$.sqlExecute(
  "INSERT INTO onepay_aca_period_segment (" +
  "track_id, employee_id, period_code, start_dt, end_dt, " +
  "locked_status, is_protected, source_eval_id, " +
  "insert_user_id, version_user_id" +
  ") VALUES (" +
  "${req.new_track_id}, ${req.to_emp}, " +
  "${req.period_code}, '${req.start_dt}'::date, '${req.end_dt}'::date, " +
  "${req.locked_status}, ${req.is_protected}, NULL, " +
  "${req.insert_user_id}, ${req.version_user_id})",
  {
    new_track_id: newTrackId,
    to_emp: toId,
    period_code: safeToNumber(r.period_code),
    start_dt: r.start_dt,
    end_dt: r.end_dt,
    locked_status: (r.locked_status === null || typeof r.locked_status === 'undefined')
      ? null : safeToNumber(r.locked_status),
    is_protected: safeToNumber(r.is_protected),
    insert_user_id: safeToNumber(r.insert_user_id),
    version_user_id: safeToNumber(r.version_user_id)
  }
);
```
Source: claude-projects/aca/redesign/aca_copy_employee_data.js:183

`_scd` is the session object in page and backend JS. Observed fields include `employeeId`, `employee_id`, `roleId`, and `user`. A `${_scd.field}` placeholder inside SQL was not observed in the source repo.

When you concatenate a string that is not a `${req.X}` placeholder, escape single quotes yourself.

```js
function dmvLookupCompanyId(companyCode) {
  var rows = $.sqlQuery(
    "SELECT company_id FROM hr_company WHERE code = '" +
    String(companyCode).replace(/'/g, "''") + "'"
  );
  return (rows && rows.length) ? num(rows[0].company_id) : null;
}
```
Source: claude-projects/dmv/dmv_payroll.js:1631

## Read rows in JavaScript

On the page, rows are `resp.data`. Guard that object. Keys are the lowercase aliases from the SELECT.

```js browser
iwb.request({
  url: "ajaxQueryData?_qid=15222",
  params: { xcompany_code: code },
  successCallback: function (resp) {
    var rows = (resp && resp.data) ? resp.data : [];
    var employees = (rows || []).map(function (r) {
      return { id: String(r.id), dsc: String(r.dsc) };
    });
  }
});
```
Source: claude-projects/south_carolina/payroll_run.js:1388

On the backend, `$.sqlQuery` returns an array of row objects. When nothing matches it returns an empty array, which is truthy. A bare `if (rows)` check passed an empty result to `rows[0]` and threw a Nashorn TypeError in `sc_payroll.js` (claude-projects/south_carolina/ADVERSARY-REVIEW-2026-08-25.md:188). Guard on `rows && rows.length` before you index. Numeric columns arrive as strings. Coerce them with `num` or `parseFloat`.

```js
function num(v) {
  if (v === null || v === undefined) return 0;
  var n = parseFloat(v);
  return isNaN(n) ? 0 : n;
}
```
Source: claude-projects/dmv/dmv_payroll.js:32

```js
function dmvSumTaxableEarnings(employeeId, onepayPayrollId) {
  var rows = $.sqlQuery(
    "SELECT COALESCE(SUM(e.amount), 0) AS gross " +
    "FROM   onepay_employee_earning e " +
    "JOIN   onepay_earning_type t ON t.earning_type_id = e.earning_type_id " +
    "WHERE  e.onepay_payroll_id = " + num(onepayPayrollId) + " " +
    "  AND  e.employee_id = " + num(employeeId) + " " +
    "  AND  t.is_taxable = true" +
    "  AND  t.is_earning = true"
  );
  if (!rows || !rows.length) return 0;
  return num(rows[0].gross);
}
```
Source: claude-projects/dmv/dmv_payroll.js:1586

Direct `parseFloat` on a row field appears too. `dmv/dmv_payroll.js:2630` reads `parseFloat(rows[0].rate || 0)`.

`num` turns a missing value into `0`. Do not use it for optional numeric foreign keys.

## Insert, update, and delete rows

Use `$.sqlQuery` only for SELECT. Use `$.sqlExecute` for INSERT, UPDATE, and DELETE.

Find the table's sequence with `grep -ri "seq_.*<table>" .`. Put `NEXTVAL('seq_<table>')` in the VALUES list. Do not use `MAX(id)+1`.

The following pattern is wrong.

```js nocheck
var sql = "INSERT INTO onepay_employee_tax_payment (employee_tax_payment_id) " +
  "VALUES ((SELECT MAX(employee_tax_payment_id) + 1 FROM onepay_employee_tax_payment))";
```
The rule is stated at claude-projects/CLAUDE.md:153.

For optional numeric FKs, emit SQL NULL rather than `0`.

```js
function sqlNumOrNull(v) {
  if (v === null || v === undefined || v === '') return 'NULL';
  var n = parseFloat(v);
  return isNaN(n) ? 'NULL' : String(n);
}
```
Source: claude-projects/dmv/dmv_payroll.js:47

```js
function dmvInsertEmployeeTaxPayment(employeePayrollId, employeeId, taxTypeId, taxAgencyId, taxJurisdictionId, taxAmount, taxableWages, stateId) {
  var sql = "INSERT INTO onepay_employee_tax_payment " +
    "(employee_tax_payment_id, employee_payroll_id, employee_id, " +
    "tax_type_id, tax_agency_id, " +
    "tax_jurisdiction_id, tax_amount, taxable_wages, state_id) VALUES (" +
    "NEXTVAL('seq_onepay_employee_tax_payment'), " +
    sqlNumOrNull(employeePayrollId) + ", " +
    sqlNumOrNull(employeeId) + ", " +
    sqlNumOrNull(taxTypeId) + ", " +
    sqlNumOrNull(taxAgencyId) + ", " +
    sqlNumOrNull(taxJurisdictionId) + ", " +
    sqlNumOrNull(taxAmount) + ", " +
    sqlNumOrNull(taxableWages) + ", " +
    sqlNumOrNull(stateId) + ")";
  return $.sqlExecute(sql);
}
```
Source: claude-projects/dmv/dmv_payroll.js:5487

DELETE uses `$.sqlExecute` at `dmv/dmv_payroll.js:5441`. UPDATE uses it at line 5595.

When you write a seeding INSERT, omit columns you would set to NULL.

The older write path is `$.postForm(formId, action, payload)`. Action `2` inserts. Action `1` edits. Migrated call sites keep the form ID in a comment so the immutable-ID hook still sees it.

```js
function insertEmployeeTaxPayment(employeePayrollId, employeeId, taxTypeId, taxAgencyId, taxJurisdictionId, taxAmount, taxableWages, stateId) {
  return $.postForm(14585, 2, {
    employee_payroll_id: employeePayrollId,
    employee_id: employeeId,
    tax_type_id: taxTypeId,
    tax_agency_id: taxAgencyId,
    tax_jurisdiction_id: taxJurisdictionId,
    tax_amount: taxAmount,
    taxable_wages: taxableWages,
    state_id: stateId
  });
}
```
Source: claude-projects/south_carolina/fnc_net_pay_nj.js:3326

ACA inserts that rely on BIGSERIAL omit the PK and re-query the new id. Search the DDL before you pick either pattern.

The platform owns the transaction around a backend function. Do not add transaction-control SQL.

## Look up tables in the DDL

Open `shared/ddl/onepay_tables.sql`, `hr_tables.sql`, and `pyr_tables.sql` before you name a column. `shared/ddl/relationships.md` lists the joins.

Prefixes:

- `onepay_*` is core payroll processing.
- `hr_*` is companies, employees, and rates.
- `pyr_*` is ADP import staging.

Key tables:

- `hr_company` holds company config. `pay_type` `0` is weekly. `pay_type` `1` is biweekly. Join to payroll on `hr_company.code` = `onepay_payroll.company_code`.
- `adp_employee` is the employee master. Child tables join on `employee_id`.
- `hr_work_assignment` holds work assignments. Select the active row with `lkp_work_assignment_status = 1`. The table also has `default_flag`. Do not use `default_flag` for that filter.
- `onepay_payroll` is one payroll run. `onepay_employee_payroll` is the per-employee summary, keyed by `payroll_id`.
- `onepay_employee_earning`, `onepay_employee_tax_payment`, `onepay_employee_deduction_payment`, and `onepay_employee_pay_item_result` join the employee payroll row on `employee_payroll_id`.
- `pyr_payroll_import_adp` is the ADP import staging table.

## Hand SQL to a human when you have no database

Live DB access from this repo's agent sessions is disabled. Do not run `dmv/tools/run_safe_query.js`. Hand copy-paste SQL for DBeaver. Work from the result the human pastes back.

When C2S itself runs the backend function, `$.sqlQuery` and `$.sqlExecute` do hit the database. That path is the platform, not your local shell.

## Rules the validators enforce

The three hooks in `dmv/.claude/hooks/` run on `Edit`, `Write`, and `MultiEdit` of `*.js` files. They inspect added lines.

- `validate-sql-conventions.sh` blocks `(FROM|JOIN|UPDATE|INTO|TABLE)` followed by `public.`. The hook reason is `schema-prefix forbidden` and names those `public.` table refs.
- `validate-sql-conventions.sh` blocks `\bdefault_flag\b` when the same file also contains `hr_work_assignment`. Active work-assignment rows use `lkp_work_assignment_status=1`, not `default_flag`.
- `validate-sql-conventions.sh` blocks a single line that calls `sqlQuery(` or `sqlExecute(` and contains `${...}` unless the placeholder starts with `${req.`, `${_scd.`, or `${process.env`. The hook reason is that SQL placeholders must be `${req.field}` or `${_scd.field}`. Multi-line queries are out of scope for that regex.
- `validate-no-tx-control.sh` blocks a quoted string that starts with `BEGIN`, `COMMIT`, `ROLLBACK`, `SAVEPOINT`, `START TRANSACTION`, or `END TRANSACTION`. The platform owns the transaction. The hook reason says to raise all-or-nothing needs with the user rather than adding those keywords.
- `validate-immutable-ids.sh` compares ID tokens on removed lines to ID tokens on added lines using regexes in `.claude/policy/immutable-id-patterns.txt`. Observed patterns include `$.postForm(<digits>`, `$.sqlQuery(<digits>`, `$.sqlExecute(<digits>`, and annotations of the form `QID 1234` or `Function ID = 1234`. A changed token set is blocked as an immutable ID edit.
- `validate-immutable-ids.sh` also reads `.claude/policy/immutable-id-registry.txt`. Each line has the form `KEY=VALUE`. If a registered VALUE appears on a removed line and is absent from the file after the edit, the hook reports that a registered platform ID cannot be changed.
