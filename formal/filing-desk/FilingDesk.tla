---------------------------- MODULE FilingDesk ----------------------------
(***************************************************************************)
(* Model of how one JVM files a return: FilingDesk.file, FileJournal,      *)
(* IrsSession.call and IrsWire.transmit, all in                            *)
(* mef-spring-boot-integration/src/main/java/com/irs/mef/filing/.          *)
(*                                                                         *)
(* Each thread is one POST /api/mef/filings request (FilingController).    *)
(* Every body in Bodies has the same client EIN, form and tax period, so   *)
(* two bodies are two different byte strings for one client-quarter.      *)
(* The IRS side (irs, ack) is what the IRS holds, not what we believe.     *)
(*                                                                         *)
(* Each action names the Java code it models. An action is one atomic      *)
(* step because the code it models runs under one lock (the FileJournal    *)
(* monitor or the IrsSession lock) or touches no shared state.             *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS
    Threads,      \* concurrent POST /api/mef/filings requests
    Bodies,       \* distinct client bodies for one EIN, form and period
    MaxRequests,  \* bound: total requests, so also total submission ids
    MaxCrashes,   \* bound: JVM crashes
    Mutation,     \* "none" models the code; any other value is a broken variant
    NoThread

Mutations == {
    "none",
    "reserveSkipsClaim",        \* FileJournal.reserve without the AlreadyClaimed check
    "unknownTransportReleases", \* IrsWire.classify maps transport-unknown to Rejected
    "reserveNotDurable",        \* FileJournal.reserve without append(created)
    "periodGuardInsideReserve"  \* a fix sketch: guardDuplicatePeriod inside reserve's lock
}
ASSUME Mutation \in Mutations
ASSUME NoThread \notin Threads

Sids == 1..MaxRequests
Rows == {"absent", "CREATED", "TRANSMITTED", "REJECTED", "INDETERMINATE", "ABANDONED"}
Outcomes == {"none", "TRANSMITTED", "REJECTED", "INDETERMINATE"}
Pcs == {"idle", "lookup", "guardRead", "guardCheck", "reserve",
        "lock", "wire", "abandon", "complete"}

VARIABLES
    journal,     \* sid -> JournalState of the row, "absent" when no row (disk == memory)
    bodyOf,      \* sid -> body the row was reserved for (its clientRequestId)
    reservedAt,  \* sid -> position of its CREATED line in the journal file, 0 if none
    irs,         \* sid -> body the IRS holds under that submission id, or "none"
    ack,         \* sid -> "none" | "Accepted" | "Rejected", as AckStore records it
    lockHolder,  \* IrsSession.lock
    pc,          \* thread -> where it is in FilingDesk.file
    body,        \* thread -> the body it files
    sid,         \* thread -> the submission id it minted in preflight
    snap,        \* thread -> journal rows as journal.findFiledFor returned them
    outcome,     \* thread -> FilingOutcome state returned by gateway.transmit
    requests, reserves, crashes

vars == <<journal, bodyOf, reservedAt, irs, ack, lockHolder,
          pc, body, sid, snap, outcome, requests, reserves, crashes>>

journalVars == <<journal, bodyOf, reservedAt, reserves>>
irsVars == <<irs, ack>>

\* SubmissionRecord.releasesIdempotencyKey
ReleasesKey(row) == row \in {"REJECTED", "ABANDONED"}

\* FileJournal.submissionIdByRequestKey: the latest row reserved for the body.
\* In memory, index() overwrites it on each reserve; on startup, replay() rebuilds
\* it in the order the CREATED lines appear in the file. Both give the latest.
KeyIndex(b) ==
    LET rows == {s \in Sids : bodyOf[s] = b /\ journal[s] # "absent"}
    IN  IF rows = {} THEN 0
        ELSE CHOOSE s \in rows : \A o \in rows : reservedAt[o] <= reservedAt[s]

\* The test in FilingDesk.file and in FileJournal.reserve.
Claimed(b) == LET k == KeyIndex(b) IN k # 0 /\ ~ReleasesKey(journal[k])

\* FilingDesk.blocksPeriod over the rows findFiledFor returned. Row states come
\* from the snapshot; acks are read live from AckStore, as the code does.
BlocksPeriod(rowsSeen) ==
    \E s \in Sids :
        \/ rowsSeen[s] \in {"CREATED", "INDETERMINATE"}
        \/ rowsSeen[s] = "TRANSMITTED" /\ ack[s] # "Rejected"

Held(s) == irs[s] # "none"

Init ==
    /\ journal = [s \in Sids |-> "absent"]
    /\ bodyOf = [s \in Sids |-> "none"]
    /\ reservedAt = [s \in Sids |-> 0]
    /\ irs = [s \in Sids |-> "none"]
    /\ ack = [s \in Sids |-> "none"]
    /\ lockHolder = NoThread
    /\ pc = [t \in Threads |-> "idle"]
    /\ body = [t \in Threads |-> "none"]
    /\ sid = [t \in Threads |-> 0]
    /\ snap = [t \in Threads |-> [s \in Sids |-> "absent"]]
    /\ outcome = [t \in Threads |-> "none"]
    /\ requests = 0
    /\ reserves = 0
    /\ crashes = 0

Goto(t, where) == pc' = [pc EXCEPT ![t] = where]

\* A request arrives. FilingDesk.preflight mints the submission id (SubmissionId.mint).
Start(t) ==
    /\ pc[t] = "idle"
    /\ requests < MaxRequests
    /\ \E b \in Bodies : body' = [body EXCEPT ![t] = b]
    /\ sid' = [sid EXCEPT ![t] = requests + 1]
    /\ requests' = requests + 1
    /\ Goto(t, "lookup")
    /\ UNCHANGED <<journalVars, irsVars, lockHolder, snap, outcome, crashes>>

\* FilingDesk.file: journal.findByClientRequestId, then replay when not released.
Lookup(t) ==
    /\ pc[t] = "lookup"
    /\ IF Claimed(body[t]) THEN Goto(t, "idle")
       ELSE IF Mutation = "periodGuardInsideReserve" THEN Goto(t, "reserve")
       ELSE Goto(t, "guardRead")
    /\ UNCHANGED <<journalVars, irsVars, lockHolder, body, sid, snap, outcome, requests, crashes>>

\* FilingDesk.guardDuplicatePeriod: journal.findFiledFor (one synchronized call).
GuardRead(t) ==
    /\ pc[t] = "guardRead"
    /\ snap' = [snap EXCEPT ![t] = journal]
    /\ Goto(t, "guardCheck")
    /\ UNCHANGED <<journalVars, irsVars, lockHolder, body, sid, outcome, requests, crashes>>

\* FilingDesk.guardDuplicatePeriod: filter(blocksPeriod) reads AckStore per row.
\* One step here, several reads in the code. Acks only move from "none" to a
\* final value, and only "Rejected" unblocks, so the reads pass exactly when one
\* read at the time of the last of them would pass.
GuardCheck(t) ==
    /\ pc[t] = "guardCheck"
    /\ IF BlocksPeriod(snap[t]) THEN Goto(t, "idle") ELSE Goto(t, "reserve")
    /\ UNCHANGED <<journalVars, irsVars, lockHolder, body, sid, snap, outcome, requests, crashes>>

\* FileJournal.reserve (synchronized): claim check, append + fsync, index.
Reserve(t) ==
    /\ pc[t] = "reserve"
    /\ LET s == sid[t]
           b == body[t]
           refused == \/ Claimed(b) /\ Mutation # "reserveSkipsClaim"
                      \/ Mutation = "periodGuardInsideReserve" /\ BlocksPeriod(journal)
       IN  IF refused
           THEN /\ Goto(t, "idle")
                /\ UNCHANGED journalVars
           ELSE /\ journal' = [journal EXCEPT ![s] = "CREATED"]
                /\ bodyOf' = [bodyOf EXCEPT ![s] = b]
                /\ reservedAt' = [reservedAt EXCEPT ![s] = reserves + 1]
                /\ reserves' = reserves + 1
                /\ Goto(t, "lock")
    /\ UNCHANGED <<irsVars, lockHolder, body, sid, snap, outcome, requests, crashes>>

\* IrsSession.call: lock.tryLock succeeds.
AcquireLock(t) ==
    /\ pc[t] = "lock"
    /\ lockHolder = NoThread
    /\ lockHolder' = t
    /\ Goto(t, "wire")
    /\ UNCHANGED <<journalVars, irsVars, body, sid, snap, outcome, requests, crashes>>

\* IrsSession.call: tryLock times out -> FilingBusyException -> FilingDesk.transmit abandons.
LockTimeout(t) ==
    /\ pc[t] = "lock"
    /\ lockHolder \notin {NoThread, t}
    /\ Goto(t, "abandon")
    /\ UNCHANGED <<journalVars, irsVars, lockHolder, body, sid, snap, outcome, requests, crashes>>

\* Inside IrsSession.call: open() and IrsWire.transmit, then unlock in finally.
\* Pre-wire failures are IrsLoginException from open() and CompositionException
\* from prepare() or Method.invoke access checks.
\* Assumption A1: an SDK ServiceException (FilingOutcome.Rejected) means the IRS
\* filed nothing under that submission id.
Wire(t) ==
    /\ pc[t] = "wire"
    /\ lockHolder' = NoThread
    /\ \/ /\ Goto(t, "abandon")
          /\ UNCHANGED <<irs, outcome>>
       \/ \E result \in {"Transmitted", "Rejected", "IndeterminateHeld", "IndeterminateNone"} :
            /\ irs' = IF result \in {"Transmitted", "IndeterminateHeld"}
                      THEN [irs EXCEPT ![sid[t]] = body[t]]
                      ELSE irs
            /\ outcome' = [outcome EXCEPT ![t] =
                   CASE result = "Transmitted" -> "TRANSMITTED"
                     [] result = "Rejected"    -> "REJECTED"
                     [] OTHER -> IF Mutation = "unknownTransportReleases"
                                 THEN "REJECTED" ELSE "INDETERMINATE"]
            /\ Goto(t, "complete")
    /\ UNCHANGED <<journalVars, ack, body, sid, snap, requests, crashes>>

\* FileJournal.abandon (synchronized). SubmissionRecord.abandoned requires CREATED.
Abandon(t) ==
    /\ pc[t] = "abandon"
    /\ journal' = [journal EXCEPT ![sid[t]] = "ABANDONED"]
    /\ Goto(t, "idle")
    /\ UNCHANGED <<bodyOf, reservedAt, reserves, irsVars, lockHolder, body, sid, snap, outcome, requests, crashes>>

\* FilingDesk.completeQuietly: journal.complete, or the append throws and the
\* row stays CREATED (memory is updated only after a successful append).
Complete(t) ==
    /\ pc[t] = "complete"
    /\ \/ journal' = [journal EXCEPT ![sid[t]] = outcome[t]]
       \/ UNCHANGED journal
    /\ outcome' = [outcome EXCEPT ![t] = "none"]
    /\ Goto(t, "idle")
    /\ UNCHANGED <<bodyOf, reservedAt, reserves, irsVars, lockHolder, body, sid, snap, requests, crashes>>

\* The JVM dies and restarts. Threads and the lock are gone. The FileJournal
\* constructor replays the file and promoteCrashedRows turns CREATED into INDETERMINATE.
CrashAndRestart ==
    /\ crashes < MaxCrashes
    /\ crashes' = crashes + 1
    /\ LET lost(s) == Mutation = "reserveNotDurable" /\ journal[s] = "CREATED"
       IN  /\ journal' = [s \in Sids |->
                  IF journal[s] # "CREATED" THEN journal[s]
                  ELSE IF lost(s) THEN "absent" ELSE "INDETERMINATE"]
           /\ bodyOf' = [s \in Sids |-> IF lost(s) THEN "none" ELSE bodyOf[s]]
           /\ reservedAt' = [s \in Sids |-> IF lost(s) THEN 0 ELSE reservedAt[s]]
    /\ pc' = [t \in Threads |-> "idle"]
    /\ lockHolder' = NoThread
    /\ outcome' = [t \in Threads |-> "none"]
    /\ UNCHANGED <<reserves, irsVars, body, sid, snap, requests>>

\* The IRS processes a return it holds; FilingDesk.ack records it in AckStore.
AckArrives(s) ==
    /\ Held(s)
    /\ ack[s] = "none"
    /\ \E verdict \in {"Accepted", "Rejected"} : ack' = [ack EXCEPT ![s] = verdict]
    /\ UNCHANGED <<journalVars, irs, lockHolder, pc, body, sid, snap, outcome, requests, crashes>>

Next ==
    \/ \E t \in Threads :
         \/ Start(t) \/ Lookup(t) \/ GuardRead(t) \/ GuardCheck(t) \/ Reserve(t)
         \/ AcquireLock(t) \/ LockTimeout(t) \/ Wire(t) \/ Abandon(t) \/ Complete(t)
    \/ CrashAndRestart
    \/ \E s \in Sids : AckArrives(s)

Spec == Init /\ [][Next]_vars

----------------------------------------------------------------------------
TypeOK ==
    /\ journal \in [Sids -> Rows]
    /\ bodyOf \in [Sids -> Bodies \cup {"none"}]
    /\ irs \in [Sids -> Bodies \cup {"none"}]
    /\ ack \in [Sids -> {"none", "Accepted", "Rejected"}]
    /\ lockHolder \in Threads \cup {NoThread}
    /\ pc \in [Threads -> Pcs]
    /\ outcome \in [Threads -> Outcomes]

\* P1. The IRS never holds the same body under two submission ids.
SameBytesFiledAtMostOnce ==
    \A b \in Bodies : Cardinality({s \in Sids : irs[s] = b}) <= 1

\* P2. Without allowDuplicatePeriod, the IRS never holds two returns for one
\* client-quarter that it has not rejected.
OneLiveReturnPerPeriod ==
    Cardinality({s \in Sids : Held(s) /\ ack[s] # "Rejected"}) <= 1

\* P3. A submission the IRS holds is never journaled as safe to send again.
HeldNeverReleased ==
    \A s \in Sids : Held(s) => ~ReleasesKey(journal[s])

\* P4. Write-ahead: a submission the IRS holds always has a journal row.
HeldIsJournaled ==
    \A s \in Sids : Held(s) => journal[s] # "absent"

=============================================================================
