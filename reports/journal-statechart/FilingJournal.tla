---- MODULE FilingJournal ----
\* One set of client bytes (one content key) sent by two requests at once,
\* through FilingDesk.file, FileJournal, and one IRS SendSubmissions call each.
EXTENDS Naturals, Sequences, FiniteSets

CONSTANTS Callers, MaxCrashes

VARIABLES
    rows,     \* journal rows for these bytes, one per reservation: [state, wire]
    pc,       \* where each request is
    mine,     \* the row a request reserved (0 = none)
    answer,   \* the IRS answer a request holds but has not written to the journal
    up,       \* the server process is running
    crashes

vars == <<rows, pc, mine, answer, up, crashes>>

Releases(s) == s \in {"abandoned", "rejected"}

\* The row that holds the content key, 0 when the key is free.
HolderSet == {i \in 1..Len(rows) : ~Releases(rows[i].state)}
Holder == IF HolderSet = {} THEN 0 ELSE CHOOSE i \in HolderSet : TRUE

StateOf(a) == CASE a = "ok"      -> "transmitted"
                [] a = "fault"   -> "rejected"
                [] a = "unknown" -> "indeterminate"

SetRow(i, field, val) == rows' = [rows EXCEPT ![i][field] = val]

Init ==
    /\ rows = <<>>
    /\ pc = [c \in Callers |-> "idle"]
    /\ mine = [c \in Callers |-> 0]
    /\ answer = [c \in Callers |-> "none"]
    /\ up = TRUE
    /\ crashes = 0

\* FilingDesk.file: findByClientRequestId finds a row that holds the key.
ReplaySaved(c) ==
    /\ up /\ pc[c] = "idle" /\ Holder # 0
    /\ rows[Holder].state # "created"
    /\ pc' = [pc EXCEPT ![c] = "done"]
    /\ UNCHANGED <<rows, mine, answer, up, crashes>>

ToldToReconcile(c) ==
    /\ up /\ pc[c] \in {"idle", "checked"} /\ Holder # 0
    /\ rows[Holder].state = "created"
    /\ pc' = [pc EXCEPT ![c] = "done"]
    /\ UNCHANGED <<rows, mine, answer, up, crashes>>

\* Lookup finds the key free; preflight passes.
LookupFree(c) ==
    /\ up /\ pc[c] = "idle" /\ Holder = 0
    /\ pc' = [pc EXCEPT ![c] = "checked"]
    /\ UNCHANGED <<rows, mine, answer, up, crashes>>

\* journal.reserve re-checks atomically; AlreadyClaimed replays a settled row.
ReserveLosesToSaved(c) ==
    /\ up /\ pc[c] = "checked" /\ Holder # 0
    /\ rows[Holder].state # "created"
    /\ pc' = [pc EXCEPT ![c] = "done"]
    /\ UNCHANGED <<rows, mine, answer, up, crashes>>

Reserve(c) ==
    /\ up /\ pc[c] = "checked" /\ Holder = 0
    /\ rows' = Append(rows, [state |-> "created", wire |-> "untouched"])
    /\ mine' = [mine EXCEPT ![c] = Len(rows) + 1]
    /\ pc' = [pc EXCEPT ![c] = "reserved"]
    /\ UNCHANGED <<answer, up, crashes>>

\* Lock timeout, login failure, or composition error: before invoke, journal.abandon.
GiveUpBeforeSend(c) ==
    /\ up /\ pc[c] = "reserved"
    /\ SetRow(mine[c], "state", "abandoned")
    /\ pc' = [pc EXCEPT ![c] = "done"]
    /\ UNCHANGED <<mine, answer, up, crashes>>

\* SendSubmissions invoke. A SOAP fault means the IRS filed nothing; any other
\* outcome means the IRS holds or may hold the bytes.
Send(c) ==
    /\ up /\ pc[c] = "reserved"
    /\ \E a \in {"ok", "fault", "unknown"} :
        /\ SetRow(mine[c], "wire", IF a = "fault" THEN "refused" ELSE "held")
        /\ answer' = [answer EXCEPT ![c] = a]
    /\ pc' = [pc EXCEPT ![c] = "onwire"]
    /\ UNCHANGED <<mine, up, crashes>>

\* completeQuietly: journal.complete succeeds.
SaveAnswer(c) ==
    /\ up /\ pc[c] = "onwire"
    /\ SetRow(mine[c], "state", StateOf(answer[c]))
    /\ pc' = [pc EXCEPT ![c] = "done"]
    /\ answer' = [answer EXCEPT ![c] = "none"]
    /\ UNCHANGED <<mine, up, crashes>>

\* completeQuietly: journal.complete throws; the row stays CREATED.
SaveFails(c) ==
    /\ up /\ pc[c] = "onwire"
    /\ pc' = [pc EXCEPT ![c] = "done"]
    /\ answer' = [answer EXCEPT ![c] = "none"]
    /\ UNCHANGED <<rows, mine, up, crashes>>

\* The JVM dies. Requests in progress are lost; a request not yet made can come later.
Crash ==
    /\ up /\ crashes < MaxCrashes
    /\ up' = FALSE
    /\ crashes' = crashes + 1
    /\ pc' = [c \in Callers |-> IF pc[c] \in {"idle", "done"} THEN pc[c] ELSE "gone"]
    /\ answer' = [c \in Callers |-> "none"]
    /\ UNCHANGED <<rows, mine>>

\* FileJournal startup replay promotes each CREATED row to INDETERMINATE.
Restart ==
    /\ ~up
    /\ up' = TRUE
    /\ rows' = [i \in 1..Len(rows) |->
                  IF rows[i].state = "created"
                  THEN [rows[i] EXCEPT !.state = "indeterminate"]
                  ELSE rows[i]]
    /\ UNCHANGED <<pc, mine, answer, crashes>>

Done ==
    /\ up /\ \A c \in Callers : pc[c] \in {"done", "gone"}
    /\ UNCHANGED vars

Next ==
    \/ \E c \in Callers :
        \/ ReplaySaved(c) \/ ToldToReconcile(c) \/ LookupFree(c)
        \/ ReserveLosesToSaved(c) \/ Reserve(c) \/ GiveUpBeforeSend(c)
        \/ Send(c) \/ SaveAnswer(c) \/ SaveFails(c)
    \/ Crash \/ Restart \/ Done

Spec == Init /\ [][Next]_vars

\* ---- Rules ----

\* The IRS never holds, or may hold, two copies of the same bytes.
AtMostOneCopyAtIrs == Cardinality({i \in 1..Len(rows) : rows[i].wire = "held"}) <= 1

\* The bytes are freed for a new send only when the IRS filed nothing.
FreedOnlyWhenNothingFiled ==
    \A i \in 1..Len(rows) : Releases(rows[i].state) => rows[i].wire # "held"

\* While the server runs, every open (CREATED) row has a request working on it.
NoOrphanedOpenRow ==
    up => \A i \in 1..Len(rows) :
            rows[i].state = "created" =>
                \E c \in Callers : pc[c] \in {"reserved", "onwire"} /\ mine[c] = i

\* A send the IRS refused is never recorded as "the IRS may have it".
RefusedSendStaysFree ==
    \A i \in 1..Len(rows) : rows[i].wire = "refused" => rows[i].state \in {"created", "rejected"}
====
