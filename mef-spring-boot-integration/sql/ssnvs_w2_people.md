# ssnvs_w2_people.sql

Header comments for `sql/ssnvs_w2_people.sql`. C2S rejects comments in a query, so they live here.

```text
-- Purpose: one row per employee who has an active W-2 for the parameter tax year
--          under the parameter legal company. Feeds the SSNVS bulk request builder:
--          ./ssnvs.sh build --csv
--          w2_box_b_ein, ssn, hr_ssn_digits, w2_box_e_first_middle, and
--          w2_box_e_last_name are NULL when that employee's active W-2s disagree
--          or any of those values is blank.
-- Params:  xtax_year (int), xlegal_company_id (int). Both are written as
--          '${req.xtax_year}'::int and '${req.xlegal_company_id}'::int.
-- Tables:  onepay_w2_form — shared/ddl/onepay_tables.sql:642
--          hr_company — shared/ddl/hr_tables.sql:14
--          adp_employee — shared/ddl/hr_tables.sql:40
--          hr_employee — shared/ddl/hr_tables.sql:277 (the unqualified table; the
--          tenant copy at shared/ddl/hr_tables.sql:137 is not used)
--          onepay_legal_company — onepay_tables.sql:2915 (repo-root dump
--          /Users/ulugbekirmatov/Documents/claude-projects/onepay_tables.sql;
--          this table is not in shared/ddl)
-- Output aliases, in order:
--   legal_company_id — onepay_legal_company.legal_company_id — onepay_tables.sql:2916
--   employer_ein — digits of onepay_legal_company.fein_number — onepay_tables.sql:2918
--   legal_company_is_active — onepay_legal_company.is_active — onepay_tables.sql:2932
--   tax_year — the xtax_year parameter; every kept row has onepay_w2_form.tax_year equal to it — shared/ddl/onepay_tables.sql:674
--   employee_id — onepay_w2_form.employee_id — shared/ddl/onepay_tables.sql:644
--   active_w2_count — count of distinct onepay_w2_form.w2_form_id — shared/ddl/onepay_tables.sql:643
--   w2_form_ids — comma-joined distinct onepay_w2_form.w2_form_id — shared/ddl/onepay_tables.sql:643
--   company_codes — comma-joined distinct onepay_w2_form.company_code — shared/ddl/onepay_tables.sql:687
--   w2_box_b_ein — digits of onepay_w2_form.box_b_employer_ein, NULL on disagreement or blank — shared/ddl/onepay_tables.sql:647
--   ssn — digits of onepay_w2_form.ssn, NULL on disagreement or blank — shared/ddl/onepay_tables.sql:645
--   hr_ssn_digits — digits of hr_employee.government_no, same agreement rule — shared/ddl/hr_tables.sql:370
--   w2_box_e_first_middle — onepay_w2_form.box_e_first_middle, NULL on disagreement or blank — shared/ddl/onepay_tables.sql:650
--   w2_box_e_last_name — onepay_w2_form.box_e_last_name, NULL on disagreement or blank — shared/ddl/onepay_tables.sql:651
--   hr_first_name — hr_employee.first_name — shared/ddl/hr_tables.sql:280
--   hr_middle_name — hr_employee.middle_name — shared/ddl/hr_tables.sql:365
--   hr_middle_initial — hr_employee.middle_initial — shared/ddl/hr_tables.sql:349
--   hr_last_name — hr_employee.last_name — shared/ddl/hr_tables.sql:279
--   adp_first_name — adp_employee.first_name — shared/ddl/hr_tables.sql:43
--   adp_middle_name — adp_employee.middle_name — shared/ddl/hr_tables.sql:44
--   adp_last_name — adp_employee.last_name — shared/ddl/hr_tables.sql:45
--   hr_birth_dt — hr_employee.birth_dt, the date column as stored — shared/ddl/hr_tables.sql:281
--   adp_birth_dt — adp_employee.birth_dt, the date column as stored — shared/ddl/hr_tables.sql:48
-- Join and filter columns:
--   hr_company.code — shared/ddl/hr_tables.sql:18
--   hr_company.legal_company_id — shared/ddl/hr_tables.sql:29
--   hr_employee.employee_id — shared/ddl/hr_tables.sql:278
--   adp_employee.employee_id — shared/ddl/hr_tables.sql:41
--   onepay_w2_form.is_active — shared/ddl/onepay_tables.sql:668
-- DBeaver: replace ${req.xtax_year} and ${req.xlegal_company_id}, keeping the
--          quotes, so each stays a quoted literal cast to int (for example
--          '2025'::int). Run the statement. Export CSV with a header row,
--          comma separator, double-quote quoting, and ISO dates. Save the file
--          outside any git repo. Never paste rows into a chat: they hold SSNs,
--          names, and birth dates.
-- QID: not registered (0). Never guess one.
```
