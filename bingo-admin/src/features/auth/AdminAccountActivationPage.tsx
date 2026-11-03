import { Alert, Button, Container, Paper, Stack, TextField, Typography } from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { activateAdminAccount } from "../../api/admin";
import { getFieldError, parseApiError } from "../../api/types";
import { useTranslation } from "react-i18next";
import { LanguageSelector } from "../../components/LanguageSelector";

export function AdminAccountActivationPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get("token") ?? "");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const activationMutation = useMutation({
    mutationFn: () => activateAdminAccount({ token, newPassword }),
    onSuccess: () => {
      setSuccessMessage("activation.success");
      setErrorMessage(null);
      setNewPassword("");
      setConfirmPassword("");
      navigate("/activate-account", { replace: true });
    },
    onError: (error) => {
      const parsed = parseApiError(error);
      if (parsed.cancelled) {
        setErrorMessage(null);
      } else if (parsed.status === 0) {
        setErrorMessage("login.noConnection");
      } else if (parsed.status === 401 || parsed.status === 403 || parsed.status === 404) {
        setErrorMessage("activation.invalidCredentials");
      } else {
        setErrorMessage("activation.unexpected");
      }
      setSuccessMessage(null);
    },
  });
  const newPasswordError = getFieldError(activationMutation.error, "newPassword")
    ? t("activation.passwordRequirements")
    : undefined;
  const confirmationError = newPassword !== confirmPassword && Boolean(confirmPassword)
    ? t("activation.passwordMismatch")
    : getFieldError(activationMutation.error, "confirmPassword")
      ? t("activation.passwordMismatch")
      : undefined;

  useEffect(() => {
    if (token && window.location.hash) {
      window.history.replaceState(window.history.state, document.title, `${location.pathname}${location.search}`);
    }
  }, [location.pathname, location.search, token]);

  return (
    <Container maxWidth="sm" sx={{ py: 8 }}>
      <Paper elevation={3} sx={{ p: 4, borderRadius: 4 }}>
        <Stack spacing={3}>
          <Stack direction="row" justifyContent="flex-end">
            <LanguageSelector />
          </Stack>
          <div>
            <Typography variant="h4" fontWeight={700} gutterBottom>
              {t("activation.title")}
            </Typography>
            <Typography color="text.secondary">
              {t("activation.description")}
            </Typography>
          </div>
          {!token && !successMessage ? (
            <Alert severity="error">{t("activation.invalidLink")}</Alert>
          ) : null}
          <TextField
            label={t("activation.newPassword")}
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            autoComplete="new-password"
            error={Boolean(newPasswordError)}
            helperText={newPasswordError ?? t("activation.passwordHint")}
            required
            fullWidth
            disabled={!token || activationMutation.isPending || Boolean(successMessage)}
          />
          <TextField
            label={t("activation.confirmPassword")}
            type="password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            autoComplete="new-password"
            error={Boolean(confirmationError)}
            helperText={confirmationError}
            required
            fullWidth
            disabled={!token || activationMutation.isPending || Boolean(successMessage)}
          />
          {errorMessage && !newPasswordError && !confirmationError ? (
            <Alert severity="error">{t(errorMessage)}</Alert>
          ) : null}
          {successMessage ? <Alert severity="success">{t(successMessage)}</Alert> : null}
          {successMessage ? (
            <Button variant="contained" onClick={() => navigate("/login")}>
              {t("activation.signIn")}
            </Button>
          ) : (
            <Button
              variant="contained"
              disabled={!token || activationMutation.isPending}
              onClick={() => {
                if (newPassword !== confirmPassword) {
                  setErrorMessage(null);
                  return;
                }
                setErrorMessage(null);
                activationMutation.mutate();
              }}
            >
              {activationMutation.isPending ? t("activation.activating") : t("activation.activate")}
            </Button>
          )}
        </Stack>
      </Paper>
    </Container>
  );
}
