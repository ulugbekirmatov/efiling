# FilingJournal

TLC reached 367 states and 643 steps. The chart draws 12 states and 19 arrows.
The chart does not draw 177 steps that stay in one chart state.
The view hides 117 steps, so the chart does not draw them.

## NoOrphanedOpenRow breaks
The shortest path has 4 steps:
0. Not filed yet
1. a request finds the bytes unfiled: Not filed yet
2. a request reserves a submission id: Submission id reserved, not sent
3. the IRS returns a receipt: IRS has or may have it, saving
4. the journal write fails: Stuck open: IRS has it, journal write failed

## RefusedSendStaysFree breaks
The shortest path has 5 steps:
0. Not filed yet
1. a request finds the bytes unfiled: Not filed yet
2. a request reserves a submission id: Submission id reserved, not sent
3. the IRS returns a SOAP fault: IRS refused it, saving
4. the server crashes: Server down, a send open
5. the server restarts: Marked unknown, though IRS refused it

## Rules that hold
AtMostOneCopyAtIrs, FreedOnlyWhenNothingFiled, NoDeadlock
