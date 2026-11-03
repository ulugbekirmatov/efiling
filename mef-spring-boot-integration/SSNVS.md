# How to verify W-2 names and SSNs with SSNVS before you file

Run this for one employer and one tax year before you upload that employer's EFW2 wage file to SSA. SSNVS (Social Security Number Verification Service) is SSA's free check of each employee's name and SSN, run in Business Services Online (BSO). It is for wage reporting only. A mismatch alone is not grounds for any action against the employee.

The tool builds the bulk request file, and you upload it in BSO. When SSA's results file comes back, the tool maps every result to an employee id and writes the list of W-2s to fix.

## Before you start

- You need a BSO account with SSNVS access. A third-party submitter registers once under its own company. You type each client's EIN on the BSO upload page.
- You need Java 17 and a compiled module (`mvn -q compile` in `mef-spring-boot-integration`).
- The export and `ssnvs.txt` hold SSNs, names, and birth dates. Keep them outside every git repo. Never paste rows into a chat.

## Export the people on the W-2s

1. Make the DBeaver copy of the query. The `.sql` file keeps the empty C2S editor markers, and PostgreSQL rejects an empty `WHERE` and `GROUP BY`:

	```sh
	sed '/^\/\*!*\*\/WHERE$/d;/^\/\*!*\*\/GROUP BY$/d' sql/ssnvs_w2_people.sql > "$HOME/ssnvs_w2_people_dbeaver.sql"
	```

2. In that copy, replace `${req.xtax_year}` and `${req.xlegal_company_id}`. Keep the quotes, so each stays a quoted literal cast to `int`.
3. Run it and export CSV with a header row, comma separators, double-quote quoting, and ISO dates.
4. Run the null-tax-year check query in `sql/ssnvs_w2_people.md`. A count above zero means active W-2s for this EIN that the export cannot include. Fix their tax year in C2S first.

One export covers one employer EIN. The tool rejects a CSV that mixes EINs or tax years.

## Build the request file

```sh
./ssnvs.sh build --csv ~/ssnvs/acme-2026.csv --out ~/ssnvs/out
```

The tool writes `~/ssnvs/out/<ein>/` and prints one line with the counts and the file to upload. That directory holds these files:

- `ssnvs.txt` holds one 130-character record per person sent. `ssnvs.zip` is also written when there are more than 500 records. Upload the zip in that case.
- `holdbacks.csv` lists each person not sent, by employee id, with the reason (`NO_SSN`, `SSN_SHAPE_NEVER_ISSUED`, `W2_FIELDS_BLANK_OR_DISAGREE`, `DUPLICATE_SSN`, and others).
- `notes.csv` lists people sent with a flag to review, such as `FIRST_MIDDLE_SPLIT_GUESSED` or `SSN_DIFFERS_FROM_HR`.
- `~/ssnvs/out/<ein>/manifest.json` maps each record's control id to the employee and the W-2 form ids. It holds no SSN, name, or birth date.

Exit code 0 means everyone was sent. Exit code 2 means you have holdbacks or a refused file to look at. Exit code 1 means the CSV could not be read, and the message names the line and the column.

To check the file independently of the Java code, run the checker written from the SSA handbook:

```sh
python3 ssnvs_check.py request ~/ssnvs/out/<ein>/ssnvs.txt
```

## Upload in BSO

1. Sign in to BSO with Login.gov or ID.me, and open SSNVS.
2. Choose the file upload option and type the client EIN that the build printed.
3. Upload `ssnvs.txt`, or `ssnvs.zip` when it exists. Keep the confirmation number.

Results are usually ready the next government business day, and you can download them for 30 days.

## Reconcile SSA's results

```sh
./ssnvs.sh results --dir ~/ssnvs/out/<ein> --results ~/Downloads/ssnvs-results.txt
python3 ssnvs_check.py pair ~/ssnvs/out/<ein>/ssnvs.txt ~/Downloads/ssnvs-results.txt
```

`fixes.csv` lists every person who did not come back verified and alive, with the action to take. It also lists any result that does not match what was sent, such as a result from another file. Exit code 0 means there is nothing to fix.

| Code | Meaning | What to do |
|---|---|---|
| blank | Verified | Nothing, unless the death indicator is `Y`. |
| 1 | SSN not in SSA's file | Check the SSN against the employee's card. Fix the master and every W-2 listed. |
| 3 | Name and SSN match, birth date does not | The W-2 is fine. Fix the birth date in HR or ADP. |
| 5 | Name does not match | Compare box e with the card. If `name_source` is `GUESSED`, confirm the first and middle split first. |
| 6 | Not verified, other reason | Ask the employee to contact SSA. |

## Fix and build again

Fix box e or the SSN in C2S, regenerate the W-2s, export again, and rebuild. The build refuses to overwrite a directory that was uploaded but not reconciled, because the next day's results need that exact file. Run `results` first, or pass `--replace` to discard the old build. Then build the EFW2 wage file.

## Sources

- SSA, *Social Security Number Verification Service (SSNVS) Handbook*, updated January 2025: the submission layout is on pages 18 and 19, and the returned layout is on pages 20 and 21.
- The C2S query and its column sources are in `sql/ssnvs_w2_people.md`.
