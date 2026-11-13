import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Box, Button, Chip, Divider, MenuItem, Paper, Stack, TextField, Typography } from "@mui/material";
import { approveDeposit, deposit, rejectDeposit, verifyDeposit } from "../../api/admin";
import { getUserFacingErrorMessage, parseApiError } from "../../api/types";
import { useSession } from "../../api/session";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { AdminTablePage } from "../../components/AdminTablePage";
import { StatusState } from "../../components/StatusState";
import { translateAdminStatus } from "../../i18n";
import { useIdempotencyKey } from "../common/useIdempotencyKey";

const rejectionReasons = ["PAYMENT_REFERENCE_NOT_FOUND", "PAYMENT_REFERENCE_MISMATCH", "SENDER_PHONE_MISMATCH", "AMOUNT_MISMATCH", "PAYMENT_NOT_SETTLED", "DUPLICATE_OR_ALREADY_USED", "OTHER"];

export function DepositDetailPage() {
  const { t } = useTranslation();
  const { depositId = "" } = useParams();
  const id = Number(depositId);
  const navigate = useNavigate();
  const session = useSession();
  const queryClient = useQueryClient();
  const idempotency = useIdempotencyKey();
  const isAdmin = session?.user.roles.includes("ADMIN") ?? false;
  const isSupport = !isAdmin && (session?.user.roles.includes("SUPPORT") ?? false);
  const detail = useQuery({ queryKey: ["deposit", id], queryFn: () => deposit(id), enabled: Number.isInteger(id) && id > 0 });
  const [reference, setReference] = useState("");
  const [reason, setReason] = useState("");
  const [outcome, setOutcome] = useState<string | null>(null);
  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["deposit", id] }),
      queryClient.invalidateQueries({ queryKey: ["deposits"] }),
      queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
    ]);
  };
  const verifyMutation = useMutation({
    mutationFn: async () => {
      const result = await verifyDeposit(id, reference.trim());
      if (result.outcome === "MATCHED") {
        await approveDeposit(id, { reviewNote: isAdmin ? "ADMIN_REVIEW_APPROVED" : "SUPPORT_REFERENCE_MATCHED", idempotencyKey: idempotency.getKey() });
      }
      return result;
    },
    onSuccess: async (result) => { setOutcome(result.outcome); await refresh(); if (result.outcome === "MATCHED") idempotency.resetKey(); },
    onError: (error) => { if (!parseApiError(error).outcomeUnknown) idempotency.resetKey(); },
  });
  const rejectMutation = useMutation({
    mutationFn: () => rejectDeposit(id, { reviewNote: reason, idempotencyKey: idempotency.getKey() }),
    onSuccess: async () => { idempotency.resetKey(); await refresh(); },
    onError: (error) => { if (!parseApiError(error).outcomeUnknown) idempotency.resetKey(); },
  });

  if (detail.isLoading) return <StatusState title={t("Loading deposits")} loading message={t("Fetching deposit queue")} />;
  if (detail.error || !detail.data) return <StatusState title={t("Unable to load deposits")} message={t(getUserFacingErrorMessage(detail.error) ?? "Something went wrong. Please try again.")} actionLabel={t("Retry")} onAction={() => detail.refetch()} />;
  const d = detail.data;
  const open = ["PENDING", "AWAITING_VERIFICATION", "VERIFICATION_EXCEPTION"].includes(d.status);
  const canAdminReview = isAdmin && d.status === "REJECTED";
  const canSupportReview = isSupport && ["PENDING", "AWAITING_VERIFICATION", "VERIFIED", "VERIFICATION_EXCEPTION"].includes(d.status);
  const anyError = verifyMutation.error ?? rejectMutation.error;

  const details: Array<[string, string]> = [
    [t("Player"), `${d.playerDisplayName || `${t("Player")} #${d.playerId}`} (#${d.playerId})`],
    [t("Phone number"), d.playerPhoneNumber ?? t("n/a")],
    [t("Payment reference"), isSupport ? `${"*".repeat(Math.max(0, d.normalizedTelebirrReference.length - 4))}${d.normalizedTelebirrReference.slice(-4)}` : d.normalizedTelebirrReference],
    [t("Submitted cash"), `${d.submittedCashAmount} ${d.currency}`],
    [t("Credit"), `${d.calculatedCreditAmount} ${d.currency}`],
    [t("Conversion rate"), d.conversionRateSnapshot],
    [t("Submitted"), new Date(d.submittedAt).toLocaleString()],
    [t("Operation ID"), d.operationId],
    [t("Verified by user #"), d.verifiedBy == null ? t("n/a") : String(d.verifiedBy)],
    [t("Verified at"), d.verifiedAt ? new Date(d.verifiedAt).toLocaleString() : t("n/a")],
    [t("Reviewed by"), d.reviewedBy == null ? t("n/a") : String(d.reviewedBy)],
    [t("Reviewed at"), d.reviewedAt ? new Date(d.reviewedAt).toLocaleString() : t("n/a")],
    [t("Review note"), d.reviewNote ?? t("n/a")],
  ];

  return <AdminTablePage><Stack spacing={2}>
    <AdminPageHeader title={`${t("Deposit details")} #${d.id}`} description={t("Review player deposit requests.")} />
    <Button onClick={() => navigate("/deposits")} sx={{ alignSelf: "flex-start", py: 0.25 }}>← {t("Back to deposits")}</Button>
    {anyError ? <Alert severity="error">{t(getUserFacingErrorMessage(anyError) ?? "Something went wrong. Please try again.")}</Alert> : null}
    {outcome && outcome !== "MATCHED" ? <Alert severity="warning">{t(outcome)}</Alert> : null}
    {verifyMutation.isSuccess && verifyMutation.data.outcome === "MATCHED" ? <Alert severity="success">{t("Deposit approved")}</Alert> : null}

    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "minmax(0, 1fr) 360px" }, alignItems: "start", gap: 2 }}>
      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 2.5 }, minWidth: 0 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2} sx={{ mb: 2 }}>
          <Typography variant="h6">{t("Deposit details")}</Typography>
          <Chip label={translateAdminStatus(d.status, t)} size="small" color={d.status === "VERIFIED" ? "success" : ["PENDING", "AWAITING_VERIFICATION", "VERIFICATION_EXCEPTION"].includes(d.status) ? "warning" : "default"} />
        </Stack>
        <Divider sx={{ mb: 2 }} />
        <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" }, columnGap: 3, rowGap: 1.75 }}>
          {details.map(([label, value]) => <Box key={label} sx={{ minWidth: 0 }}>
            <Typography variant="caption" color="text.secondary">{label}</Typography>
            <Typography variant="body2" sx={{ overflowWrap: "anywhere", fontWeight: 500 }}>{value}</Typography>
          </Box>)}
          {d.paymentInstructions?.paymentInstructions ? <Box sx={{ gridColumn: { sm: "1 / -1" } }}>
            <Typography variant="caption" color="text.secondary">{t("Payment instructions")}</Typography>
            <Typography variant="body2">{d.paymentInstructions.paymentInstructions}</Typography>
          </Box> : null}
        </Box>
      </Paper>

      {(canSupportReview || canAdminReview) ? <Paper variant="outlined" sx={{ p: 2.5, position: { md: "sticky" }, top: 96 }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6">{canAdminReview ? t("Review") : t("Deposit actions")}</Typography>
            <Typography variant="body2" color="text.secondary">{canAdminReview ? t("Review the rejection against the full payment reference. A match will credit the deposit.") : t("Verify deposit")}</Typography>
          </Box>
          {(canAdminReview || (canSupportReview && open)) ? <>
            <TextField label={t("Full payment reference")} value={reference} onChange={(e) => setReference(e.target.value)} autoComplete="off" required fullWidth />
            <Button variant="contained" disabled={!reference.trim() || verifyMutation.isPending} onClick={() => { idempotency.resetKey(); verifyMutation.mutate(); }} fullWidth>
              {canAdminReview ? t("Review and approve") : t("Verify and deposit")}
            </Button>
          </> : null}
          {canSupportReview ? <>
            <Divider />
            <Typography variant="subtitle2">{t("Reject deposit")}</Typography>
            <TextField select label={t("Rejection reason")} value={reason} onChange={(e) => setReason(e.target.value)} required fullWidth>
              {rejectionReasons.map((item) => <MenuItem key={item} value={item}>{t(item)}</MenuItem>)}
            </TextField>
            <Button color="error" variant="outlined" disabled={!reason || rejectMutation.isPending} onClick={() => { idempotency.resetKey(); rejectMutation.mutate(); }} fullWidth>{t("Reject")}</Button>
          </> : null}
          {canSupportReview && d.status === "VERIFIED" ? <Button variant="contained" disabled={verifyMutation.isPending} onClick={async () => { idempotency.resetKey(); await approveDeposit(id, { reviewNote: "SUPPORT_REFERENCE_MATCHED", idempotencyKey: idempotency.getKey() }); await refresh(); }} fullWidth>{t("Deposit")}</Button> : null}
        </Stack>
      </Paper> : null}
    </Box>
  </Stack></AdminTablePage>;
}
