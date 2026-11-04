import { Alert, Button, Container, Paper, Stack, TextField, Typography } from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef, useState, type FormEvent } from "react";
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
  const newPasswordInput = useRef<HTMLInputElement>(null);
  const confirmPasswordInput = useRef<HTMLInputElement>(null);
  const [newPasswordTouched, setNewPasswordTouched] = useState(false);
  const [confirmPasswordTouched, setConfirmPasswordTouched] = useState(false);
  const [newPasswordValid, setNewPasswordValid] = useState(false);
  const [confirmPasswordHasValue, setConfirmPasswordHasValue] = useState(false);
  const [passwordsMatch, setPasswordsMatch] = useState(false);
  const [newPasswordChangedAfterFailure, setNewPasswordChangedAfterFailure] = useState(false);
  const [confirmPasswordChangedAfterFailure, setConfirmPasswordChangedAfterFailure] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const activationMutation = useMutation({
    mutationFn: () => activateAdminAccount({
      token,
      newPassword: newPasswordInput.current?.value ?? "",
    }),
    onSuccess: () => {
      setSuccessMessage("activation.success");
      setErrorMessage(null);
      if (newPasswordInput.current) newPasswordInput.current.value = "";
      if (confirmPasswordInput.current) confirmPasswordInput.current.value = "";
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
      setNewPasswordChangedAfterFailure(false);
      setConfirmPasswordChangedAfterFailure(false);
      setSuccessMessage(null);
    },
  });
  const newPasswordError = (newPasswordTouched && !newPasswordValid) ||
    (!newPasswordChangedAfterFailure &&
      getFieldError(activationMutation.error, "newPassword"))
    ? t("activation.passwordRequirements")
    : undefined;
  const confirmationError = !confirmPasswordChangedAfterFailure &&
    getFieldError(activationMutation.error, "confirmPassword")
    ? t("activation.passwordMismatch")
    : confirmPasswordTouched
      ? !confirmPasswordHasValue
        ? t("This field is required.")
        : !passwordsMatch
          ? t("activation.passwordMismatch")
          : undefined
      : undefined;
  const canActivate =
    Boolean(token) && newPasswordValid && passwordsMatch &&
    !activationMutation.isPending && !successMessage;

  const updatePasswordValidity = (field: "newPassword" | "confirmPassword") => {
    const newPassword = newPasswordInput.current?.value ?? "";
    const confirmPassword = confirmPasswordInput.current?.value ?? "";
    const nextPasswordValid = newPassword.length >= 8 && newPassword.length <= 72;
    const nextConfirmHasValue = confirmPassword.length > 0;
    const nextPasswordsMatch = nextConfirmHasValue && newPassword === confirmPassword;
    setNewPasswordTouched((touched) => touched || Boolean(newPassword));
    setConfirmPasswordTouched((touched) => touched || nextConfirmHasValue);
    setNewPasswordValid((valid) => valid === nextPasswordValid ? valid : nextPasswordValid);
    setConfirmPasswordHasValue((hasValue) =>
      hasValue === nextConfirmHasValue ? hasValue : nextConfirmHasValue,
    );
    setPasswordsMatch((matches) =>
      matches === nextPasswordsMatch ? matches : nextPasswordsMatch,
    );
    if (field === "newPassword") {
      setNewPasswordChangedAfterFailure(true);
    } else {
      setConfirmPasswordChangedAfterFailure(true);
    }
    if (errorMessage) setErrorMessage(null);
  };

  const submitActivation = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    updatePasswordValidity("newPassword");
    updatePasswordValidity("confirmPassword");
    const newPassword = newPasswordInput.current?.value ?? "";
    const confirmPassword = confirmPasswordInput.current?.value ?? "";
    if (newPassword.length < 8 || newPassword.length > 72 ||
        !confirmPassword || newPassword !== confirmPassword) {
      setNewPasswordTouched(true);
      setConfirmPasswordTouched(true);
      return;
    }
    setErrorMessage(null);
    activationMutation.mutate();
  };

  useEffect(() => {
    if (token && window.location.hash) {
      window.history.replaceState(window.history.state, document.title, `${location.pathname}${location.search}`);
    }
  }, [location.pathname, location.search, token]);

  return (
    <Container maxWidth="sm" sx={{ py: 8 }}>
      <Paper elevation={3} sx={{ p: 4, borderRadius: 4 }}>
        <Stack component="form" spacing={3} onSubmit={submitActivation} noValidate>
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
            inputRef={newPasswordInput}
            autoComplete="new-password"
            error={Boolean(newPasswordError)}
            helperText={newPasswordError ?? t("activation.passwordHint")}
            required
            fullWidth
            inputProps={{ maxLength: 72 }}
            onChange={() => updatePasswordValidity("newPassword")}
            disabled={!token || activationMutation.isPending || Boolean(successMessage)}
          />
          <TextField
            label={t("activation.confirmPassword")}
            type="password"
            inputRef={confirmPasswordInput}
            autoComplete="new-password"
            error={Boolean(confirmationError)}
            required
            fullWidth
            helperText={confirmationError ?? t("activation.confirmPasswordHint")}
            onChange={() => updatePasswordValidity("confirmPassword")}
            disabled={!token || activationMutation.isPending || Boolean(successMessage)}
          />
          {errorMessage && !newPasswordError && !confirmationError ? (
            <Alert severity="error">{t(errorMessage)}</Alert>
          ) : null}
          {successMessage ? <Alert severity="success">{t(successMessage)}</Alert> : null}
          {successMessage ? (
            <Button type="button" variant="contained" onClick={() => navigate("/login")}>
              {t("activation.signIn")}
            </Button>
          ) : (
            <Button
              variant="contained"
              type="submit"
              disabled={!canActivate}
            >
              {activationMutation.isPending ? t("activation.activating") : t("activation.activate")}
            </Button>
          )}
          {!successMessage && token && !canActivate && !activationMutation.isPending ? (
            <Typography variant="body2" color="text.secondary">
              {t("Complete all required fields and make sure the passwords match.")}
            </Typography>
          ) : null}
        </Stack>
      </Paper>
    </Container>
  );
}
