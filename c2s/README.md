# C2S platform context for the MeF project

C2S is the payroll platform that holds the employer, employee, and payroll data the MeF service files with the IRS. It is a low-code platform with three kinds of custom code. Backend functions are ES5 JavaScript files that the platform runs inside its Nashorn engine, identified by a numeric function ID (DID). Queries are PostgreSQL SELECT statements registered on the platform, identified by a numeric query ID (QID). Pages are browser JavaScript that call functions and queries over the platform's own ajax endpoints.

The source of every pattern in these docs is the sibling repo `claude-projects` (`/Users/ulugbekirmatov/Documents/claude-projects`), where the payroll, ACA, and bonus code for C2S lives. Each code example names its source file and line. When a doc and that repo disagree, the repo wins.

## Read in this order

1. [Platform rules and gotchas](platform-rules.md). Reference. The ES5 constraints, the platform-owned transaction, immutable IDs, logging and secrets rules. Read this before writing any C2S code.
2. [How to write a C2S backend function](backend-functions.md). How-to. File skeleton, reading inputs, querying, writing, gated writes, logging, returning JSON, testing under Node.
3. [How to write C2S queries and inline SQL](queries.md). How-to. Registered queries, parameter quoting, result shapes, sequences, schema references, and the rules the validators enforce.
4. [How to call C2S functions and queries from a page](js-pages.md). How-to. The `iwb.request` call shapes for functions, queries, and forms, response unwrapping, error handling.
5. [How C2S reaches the outside world](integration.md). Email, scheduled jobs, chunked runners, and the open decision on how C2S will call the MeF REST API.

## Check the examples

Every JavaScript block in these docs is checked the way the platform would check it. The script parses each block with `node --check`, rejects ES6 tokens in backend blocks, and rejects transaction-control SQL.

```sh
bash c2s/tools/check-snippets.sh
```

A block tagged `js browser` skips the ES5 check. A block tagged `js nocheck` shows a deliberately wrong pattern and is skipped entirely.

## What these docs do not cover

They do not describe the MeF service itself. See the repo root `CLAUDE.md` and `CODE_MAP.md` for that. They do not settle how C2S and the MeF service exchange data. That decision is open and is laid out in [integration.md](integration.md).
