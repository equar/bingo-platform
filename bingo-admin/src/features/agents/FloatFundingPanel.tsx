import { useTranslation } from "react-i18next";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert, Box, Button, Card, CardContent, Chip, Grid, MenuItem, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, TextField, Typography,
} from "@mui/material";
import {
  adminFloatBankAccounts, adminFloatTopUpRequests, createFloatBankAccount,
  dailyFloatReconciliation, floatTopUpVerificationHistory, postVerifiedFloatTopUp,
  recordSyntheticFloatBankStatement, reviewFloatTopUpRequest, setFloatBankAccountActive,
  setFloatBankAccountPlayerDepositsEnabled, verifyFloatTopUp,
} from "../../api/admin";
import { getUserFacingErrorMessage, parseApiError } from "../../api/types";
import { translateAdminStatus } from "../../i18n";

const today = () => new Date().toISOString().slice(0, 10);

export function FloatFundingPanel() {
  const { t } = useTranslation();
  const client = useQueryClient();
  const [accountName, setAccountName] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [accountHolder, setAccountHolder] = useState("");
  const [status, setStatus] = useState("PENDING");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [requestId, setRequestId] = useState("");
  const [statementAccountId, setStatementAccountId] = useState("");
  const [statementReference, setStatementReference] = useState("");
  const [statementAmount, setStatementAmount] = useState("");
  const [statementDate, setStatementDate] = useState(today());
  const [settlementState, setSettlementState] = useState("SETTLED");
  const [statementEntryId, setStatementEntryId] = useState("");
  const [reviewNote, setReviewNote] = useState("");
  const [exceptionReason, setExceptionReason] = useState("");
  const [exceptionOutcome, setExceptionOutcome] = useState("MISSING_TRANSFER");
  const [reconciliationDate, setReconciliationDate] = useState(today());
  const [uncertainRequestStatuses, setUncertainRequestStatuses] = useState<Record<number, string>>({});

  const accounts = useQuery({ queryKey: ["float-bank-accounts"], queryFn: adminFloatBankAccounts });
  const requests = useQuery({
    queryKey: ["admin-float-top-ups", page, status, search],
    queryFn: () => adminFloatTopUpRequests(page, 10, status, search),
  });
  const selectedId = Number(requestId);
  const selectedRequest = (requests.data?.content ?? []).find((request) => request.id === selectedId);
  const verifications = useQuery({
    queryKey: ["float-top-up-verifications", selectedId],
    queryFn: () => floatTopUpVerificationHistory(selectedId),
    enabled: Number.isSafeInteger(selectedId) && selectedId > 0,
  });
  const accountChoices = useMemo(() => accounts.data ?? [], [accounts.data]);

  const invalidate = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ["float-bank-accounts"] }),
      client.invalidateQueries({ queryKey: ["admin-float-top-ups"] }),
      client.invalidateQueries({ queryKey: ["float-top-up-verifications"] }),
      client.invalidateQueries({ queryKey: ["agents"] }),
    ]);
  };

  const createAccount = useMutation({
    mutationFn: () => createFloatBankAccount({ bankName: accountName.trim(), accountNumber: accountNumber.trim(), accountHolderName: accountHolder.trim(), currency: "ETB" }),
    onSuccess: async () => { setAccountName(""); setAccountNumber(""); setAccountHolder(""); await invalidate(); },
  });
  const toggleAccount = useMutation({ mutationFn: ({ id, active }: { id: number; active: boolean }) => setFloatBankAccountActive(id, active), onSuccess: invalidate });
  const togglePlayerDeposits = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      setFloatBankAccountPlayerDepositsEnabled(id, enabled),
    onSuccess: invalidate,
  });
  const recordStatement = useMutation({
    mutationFn: () => recordSyntheticFloatBankStatement({
      receivingBankAccountId: Number(statementAccountId), transactionReference: statementReference.trim(),
      amount: statementAmount, currency: "ETB", transactionDate: statementDate,
      settlementState: settlementState as "SETTLED" | "UNSETTLED" | "REVERSED",
    }),
    onSuccess: async (entry) => { setStatementEntryId(String(entry.statementEntryId ?? "")); await invalidate(); },
  });
  const verify = useMutation({
    mutationFn: () => verifyFloatTopUp(selectedId, { statementEntryId: Number(statementEntryId) }),
    onSuccess: async (result) => { await invalidate(); setStatus(result.outcome === "MATCHED" ? "VERIFIED" : "VERIFICATION_EXCEPTION"); },
  });
  const addException = useMutation({
    mutationFn: () => verifyFloatTopUp(selectedId, { exceptionOutcome, exceptionReason: exceptionReason.trim() }),
    onSuccess: async () => { await invalidate(); setStatus("VERIFICATION_EXCEPTION"); },
  });
  const post = useMutation({
    mutationFn: () => postVerifiedFloatTopUp(selectedId, reviewNote.trim()),
    onSuccess: invalidate,
    onError: (error) => {
      if (parseApiError(error).outcomeUnknown && selectedRequest) {
        setUncertainRequestStatuses((current) => ({ ...current, [selectedId]: selectedRequest.status }));
      }
    },
  });
  const reject = useMutation({
    mutationFn: () => reviewFloatTopUpRequest(selectedId, { approve: false, reviewNote: reviewNote.trim() }),
    onSuccess: async () => { await invalidate(); setStatus("REJECTED"); },
    onError: (error) => {
      if (parseApiError(error).outcomeUnknown && selectedRequest) {
        setUncertainRequestStatuses((current) => ({ ...current, [selectedId]: selectedRequest.status }));
      }
    },
  });
  const reconciliation = useMutation({ mutationFn: () => dailyFloatReconciliation(reconciliationDate) });
  const actionOutcomeUnknown = Boolean(
    selectedRequest && uncertainRequestStatuses[selectedRequest.id] === selectedRequest.status,
  );
  const refreshSelectedRequestStatus = async () => {
    if (!selectedRequest || !actionOutcomeUnknown) return;
    const previousStatus = uncertainRequestStatuses[selectedRequest.id];
    const result = await requests.refetch();
    const refreshedRequest = result.data?.content.find((request) => request.id === selectedRequest.id);
    if (refreshedRequest && refreshedRequest.status !== previousStatus) {
      setUncertainRequestStatuses((current) => {
        const next = { ...current };
        delete next[selectedRequest.id];
        return next;
      });
    }
  };
  const error = [createAccount, toggleAccount, togglePlayerDeposits, recordStatement, verify, addException, post, reject, reconciliation]
    .find((mutation) => mutation.isError);

  return (
    <Stack spacing={3}>
      <Alert severity="warning">
        {t("Funding controls are in SIMULATED QA mode. Agent receipts are claims only. The synthetic statement form represents a test fixture and never contacts a bank. Production credit remains disabled pending finance approvals.")}</Alert>
      {accounts.isError ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => accounts.refetch()}>{t("Retry")}</Button>}>
          {t(getUserFacingErrorMessage(accounts.error) ?? "Something went wrong. Please try again.")}
        </Alert>
      ) : null}
      {requests.isError ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => requests.refetch()}>{t("Retry")}</Button>}>
          {t(getUserFacingErrorMessage(requests.error) ?? "Something went wrong. Please try again.")}
        </Alert>
      ) : null}
      {error?.isError ? (
        <Alert
          severity="error"
          role="alert"
          action={actionOutcomeUnknown
            ? <Button color="inherit" size="small" onClick={() => void refreshSelectedRequestStatus()}>{t("Refresh status")}</Button>
            : undefined}
        >
          {t(getUserFacingErrorMessage(error.error) ?? "Something went wrong. Please try again.")}
        </Alert>
      ) : null}

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <Card><CardContent>
            <Stack spacing={2}>
              <Typography variant="h6">{t("Receiving bank accounts")}</Typography>
              <Typography variant="body2" color="text.secondary">{t("Full account numbers are encrypted at rest. Agent and admin views show masked numbers only. Deactivation keeps historical request snapshots.")}</Typography>
              <Grid container spacing={1}>
                <Grid item xs={12} sm={4}><TextField label={t("Bank name")} value={accountName} onChange={(e) => setAccountName(e.target.value)} fullWidth /></Grid>
                <Grid item xs={12} sm={4}><TextField label={t("Account number")} value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} fullWidth /></Grid>
                <Grid item xs={12} sm={4}><TextField label={t("Account holder")} value={accountHolder} onChange={(e) => setAccountHolder(e.target.value)} fullWidth /></Grid>
              </Grid>
              <Button variant="outlined" onClick={() => createAccount.mutate()} disabled={createAccount.isPending || !accountName.trim() || accountNumber.trim().length < 4 || !accountHolder.trim()}>{t("Add receiving account")}</Button>
              <Table size="small" aria-label={t("receiving bank accounts")}><TableHead><TableRow><TableCell>{t("Bank")}</TableCell><TableCell>{t("Account")}</TableCell><TableCell>{t("Holder")}</TableCell><TableCell>{t("State")}</TableCell><TableCell>{t("Player deposits")}</TableCell><TableCell /></TableRow></TableHead>
                <TableBody>{accountChoices.map((account) => <TableRow key={account.id}><TableCell>{account.bankName}</TableCell><TableCell>{account.maskedAccountNumber}</TableCell><TableCell>{account.accountHolderName}</TableCell><TableCell>{account.active ? "Active" : "Inactive"}</TableCell><TableCell>{account.playerDepositsEnabled ? "Enabled" : "Disabled"}<Button size="small" disabled={!account.active && !account.playerDepositsEnabled} onClick={() => togglePlayerDeposits.mutate({ id: account.id, enabled: !account.playerDepositsEnabled })}>{account.playerDepositsEnabled ? "Disable" : "Enable"}</Button></TableCell><TableCell><Button size="small" onClick={() => toggleAccount.mutate({ id: account.id, active: !account.active })}>{account.active ? "Deactivate" : "Activate"}</Button></TableCell></TableRow>)}
                  {accountChoices.length === 0 ? <TableRow><TableCell colSpan={6}>{t("No receiving accounts configured.")}</TableCell></TableRow> : null}</TableBody>
              </Table>
            </Stack>
          </CardContent></Card>
        </Grid>
        <Grid item xs={12} lg={6}>
          <Card><CardContent>
            <Stack spacing={2}>
              <Typography variant="h6">{t("Synthetic QA bank statement entry")}</Typography>
              <Alert severity="info">{t("Only create entries from the synthetic QA statement fixture. This does not confirm real settlement.")}</Alert>
              <TextField select label={t("Receiving account")} value={statementAccountId} onChange={(e) => setStatementAccountId(e.target.value)} fullWidth>
                {accountChoices.filter((account) => account.active).map((account) => <MenuItem key={account.id} value={String(account.id)}>{account.bankName} {t("·")}{" "}{account.maskedAccountNumber}</MenuItem>)}
              </TextField>
              <Grid container spacing={1}>
                <Grid item xs={12} sm={6}><TextField label={t("Bank transaction reference")} value={statementReference} onChange={(e) => setStatementReference(e.target.value)} fullWidth /></Grid>
                <Grid item xs={12} sm={3}><TextField label={t("Amount ETB")} type="number" value={statementAmount} onChange={(e) => setStatementAmount(e.target.value)} fullWidth /></Grid>
                <Grid item xs={12} sm={3}><TextField label={t("Transaction date")} type="date" value={statementDate} onChange={(e) => setStatementDate(e.target.value)} InputLabelProps={{ shrink: true }} fullWidth /></Grid>
              </Grid>
              <TextField select label={t("Settlement state")} value={settlementState} onChange={(e) => setSettlementState(e.target.value)} fullWidth>
                <MenuItem value="SETTLED">{t("Settled")}</MenuItem><MenuItem value="UNSETTLED">{t("Unsettled")}</MenuItem><MenuItem value="REVERSED">{t("Reversed")}</MenuItem>
              </TextField>
              <Button variant="contained" onClick={() => recordStatement.mutate()} disabled={recordStatement.isPending || !statementAccountId || !statementReference.trim() || Number(statementAmount) <= 0}>{t("Record synthetic statement entry")}</Button>
              {statementEntryId ? <Chip color="info" label={t("Synthetic statement entry #{{id}}", { id: statementEntryId })} /> : null}
            </Stack>
          </CardContent></Card>
        </Grid>
      </Grid>

      <Card><CardContent><Stack spacing={2}>
        <Typography variant="h6">{t("Float top-up review")}</Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}><TextField label={t("Search by agent or phone")} value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} fullWidth /></Grid>
          <Grid item xs={12} md={3}><TextField select label={t("Request status")} value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} fullWidth>
            {["PENDING", "VERIFICATION_EXCEPTION", "VERIFIED", "APPROVED", "REJECTED"].map((item) => <MenuItem key={item} value={item}>{translateAdminStatus(item, t)}</MenuItem>)}
          </TextField></Grid>
          <Grid item xs={12} md={3}><TextField label={t("Page")} type="number" value={page + 1} inputProps={{ min: 1 }} onChange={(e) => setPage(Math.max(0, Number(e.target.value) - 1))} fullWidth /></Grid>
        </Grid>
        <Table size="small" aria-label={t("float top-up requests")}><TableHead><TableRow><TableCell>{t("Request")}</TableCell><TableCell>{t("Agent")}</TableCell><TableCell>{t("Amount")}</TableCell><TableCell>{t("Status")}</TableCell><TableCell>{t("Receiving account")}</TableCell><TableCell>{t("Transfer reference")}</TableCell><TableCell>{t("Action")}</TableCell></TableRow></TableHead>
          <TableBody>{(requests.data?.content ?? []).map((request) => <TableRow key={request.id} hover>
            <TableCell>{t("#")}{request.id}</TableCell><TableCell>{request.agentDisplayName}<br />{request.agentPhoneNumber}</TableCell><TableCell>{request.amount} {request.currency}</TableCell><TableCell>{translateAdminStatus(request.status, t)}</TableCell>
            <TableCell>{request.bankNameSnapshot ?? t("legacy request")}<br />{request.maskedAccountSnapshot ?? ""}</TableCell><TableCell>{request.transferReference ?? t("n/a")}<br /><Typography variant="caption">{request.supportingEvidenceNote ? t("Agent supplied claim; not bank evidence") : ""}</Typography></TableCell>
            <TableCell><Button size="small" onClick={() => setRequestId(String(request.id))}>{t("Details")}</Button></TableCell>
          </TableRow>)}
            {(requests.data?.content ?? []).length === 0 ? <TableRow><TableCell colSpan={7}>{t("No requests found.")}</TableCell></TableRow> : null}
          </TableBody>
        </Table>
        <Box display="flex" justifyContent="space-between"><Button disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>{t("Previous page")}</Button><Button disabled={!requests.data || page + 1 >= requests.data.totalPages} onClick={() => setPage((value) => value + 1)}>{t("Next page")}</Button></Box>

        <Typography variant="subtitle1">{t("Selected request details")}</Typography>
        <TextField label={t("Request id")} type="number" value={requestId} onChange={(e) => setRequestId(e.target.value)} fullWidth />
        {selectedRequest ? <Alert severity="info">{t("Request #")}{selectedRequest.id}{t(":")}{" "}{selectedRequest.amount} {selectedRequest.currency}{t(";")}{" "}{selectedRequest.bankNameSnapshot} {selectedRequest.maskedAccountSnapshot}{t("; transfer reference")}{" "}{selectedRequest.transferReference}{t(". Uploaded evidence is not independent verification.")}</Alert> : null}
        <Grid container spacing={2}>
          <Grid item xs={12} sm={4}><TextField label={t("Synthetic statement entry ID")} type="number" value={statementEntryId} onChange={(e) => setStatementEntryId(e.target.value)} fullWidth /></Grid>
          <Grid item xs={12} sm={4}><Button variant="outlined" onClick={() => verify.mutate()} disabled={verify.isPending || !selectedId || !Number(statementEntryId)}>{t("Match independent statement entry")}</Button></Grid>
          <Grid item xs={12} sm={4}><TextField select label={t("Exception outcome")} value={exceptionOutcome} onChange={(e) => setExceptionOutcome(e.target.value)} fullWidth>
            {["MISSING_TRANSFER", "CONFLICTING_EVIDENCE", "UNSETTLED_OR_REVERSED"].map((item) => <MenuItem key={item} value={item}>{item.replace(/_/g, " ")}</MenuItem>)}
          </TextField></Grid>
        </Grid>
        <TextField label={t("Exception reason")} value={exceptionReason} onChange={(e) => setExceptionReason(e.target.value)} inputProps={{ maxLength: 500 }} fullWidth />
        <Button variant="outlined" onClick={() => addException.mutate()} disabled={addException.isPending || !selectedId || !exceptionReason.trim()}>{t("Record verification exception")}</Button>
        <Typography variant="subtitle2">{t("Verification history")}</Typography>
        {(verifications.data ?? []).map((item) => <Chip key={item.id ?? `${item.outcome}-${item.verifiedAt}`} label={`${item.outcome} · ${item.evidenceSource} · ${t("Reviewer")} ${item.reviewerUserId}${item.exceptionReason ? ` · ${item.exceptionReason}` : ""}`} />)}
        {verifications.isError ? <Alert severity="error">{t(getUserFacingErrorMessage(verifications.error) ?? "Something went wrong. Please try again.")}</Alert> : null}
        <TextField label={t("Review note")} value={reviewNote} onChange={(e) => setReviewNote(e.target.value)} inputProps={{ maxLength: 500 }} multiline minRows={2} fullWidth />
        <Stack direction="row" spacing={2}>
          <Button variant="outlined" onClick={() => reject.mutate()} disabled={reject.isPending || actionOutcomeUnknown || !selectedId || !reviewNote.trim() || selectedRequest?.status !== "PENDING"}>{t("Review request (reject, no credit)")}</Button>
          <Button variant="contained" onClick={() => post.mutate()} disabled={post.isPending || actionOutcomeUnknown || !selectedId || !reviewNote.trim() || selectedRequest?.status !== "VERIFIED"}>{t("Post verified request (QA simulation)")}</Button>
        </Stack>
      </Stack></CardContent></Card>

      <Card><CardContent><Stack spacing={2}>
        <Typography variant="h6">{t("Daily float reconciliation")}</Typography>
        <Alert severity="info">{t("Read-only comparison; synthetic QA data is not real bank reconciliation. Discrepancies never create automatic adjustments.")}</Alert>
        <Stack direction="row" spacing={2}><TextField label={t("Statement date")} type="date" value={reconciliationDate} onChange={(e) => setReconciliationDate(e.target.value)} InputLabelProps={{ shrink: true }} /><Button variant="outlined" onClick={() => reconciliation.mutate()}>{t("Run read-only reconciliation")}</Button></Stack>
        {reconciliation.data ? <Alert severity={reconciliation.data.journalBalanced ? "success" : "warning"}>
          {reconciliation.data.syntheticQaData ? `${t("SIMULATED QA")} ${t("·")} ` : ""}{reconciliation.data.statementDate}{t(": settled statement")}{" "}{reconciliation.data.settledStatementAmount} {t("ETB / posted")}{" "}{reconciliation.data.postedAmount} {t("ETB; debit")}{" "}{reconciliation.data.debitTotal} {t("/ credit")}{" "}{reconciliation.data.creditTotal}{t("; unmatched")}{" "}{reconciliation.data.unmatchedSettledStatementCount}{t(", verified unposted")}{" "}{reconciliation.data.verifiedUnpostedRequestCount}{t(", exceptions")}{" "}{reconciliation.data.unresolvedExceptionCount}{t("; difference")}{" "}{reconciliation.data.statementToPostingDifference} {t("ETB.")}</Alert> : null}
      </Stack></CardContent></Card>
    </Stack>
  );
}
