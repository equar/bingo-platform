import { useTranslation } from "react-i18next";
import { Alert, Box, Button, Card, CardContent, Stack, TextField, Typography } from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { useRef, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { changeAdminPassword } from "../../api/admin";
import { readSession, writeSession } from "../../api/session";
import { getFieldError, parseApiError } from "../../api/types";

export function ChangePasswordPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const currentPasswordInput = useRef<HTMLInputElement>(null);
  const newPasswordInput = useRef<HTMLInputElement>(null);
  const confirmPasswordInput = useRef<HTMLInputElement>(null);
  const [currentPasswordPresent, setCurrentPasswordPresent] = useState(false);
  const [newPasswordValid, setNewPasswordValid] = useState(false);
  const [confirmPasswordHasValue, setConfirmPasswordHasValue] = useState(false);
  const [passwordsMatch, setPasswordsMatch] = useState(false);
  const [currentPasswordTouched, setCurrentPasswordTouched] = useState(false);
  const [newPasswordTouched, setNewPasswordTouched] = useState(false);
  const [confirmPasswordTouched, setConfirmPasswordTouched] = useState(false);
  const [currentPasswordChangedAfterFailure, setCurrentPasswordChangedAfterFailure] =
    useState(false);
  const [newPasswordChangedAfterFailure, setNewPasswordChangedAfterFailure] =
    useState(false);
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const passwordMutation = useMutation({
    mutationFn: () => changeAdminPassword({
      currentPassword: currentPasswordInput.current?.value ?? "",
      newPassword: newPasswordInput.current?.value ?? "",
    }),
    onSuccess: () => {
      const session = readSession();
      if (session) {
        writeSession({
          ...session,
          user: { ...session.user, mustChangePassword: false },
        });
      }
      setSuccessMessage("Password updated successfully.");
      setErrorMessage(null);
      if (currentPasswordInput.current) currentPasswordInput.current.value = "";
      if (newPasswordInput.current) newPasswordInput.current.value = "";
      if (confirmPasswordInput.current) confirmPasswordInput.current.value = "";
      setShowCurrentPassword(false);
      setShowNewPassword(false);
      navigate("/dashboard", { replace: true });
    },
    onError: (error) => {
      const parsed = parseApiError(error);
      setErrorMessage(parsed.message);
      setSuccessMessage(null);
      setCurrentPasswordChangedAfterFailure(false);
      setNewPasswordChangedAfterFailure(false);
    },
  });
  const parsedPasswordError = parseApiError(passwordMutation.error);
  const currentPasswordError =
    (!currentPasswordChangedAfterFailure &&
      getFieldError(passwordMutation.error, "currentPassword")) ??
    (!currentPasswordChangedAfterFailure &&
      passwordMutation.isError && parsedPasswordError.status === 401
      ? parsedPasswordError.message
      : currentPasswordTouched && !currentPasswordPresent
        ? t("This field is required.")
        : undefined);
  const newPasswordError =
    (!newPasswordChangedAfterFailure &&
      getFieldError(passwordMutation.error, "newPassword")) ??
    (newPasswordTouched && !newPasswordValid
      ? t("Password must contain 8 to 72 characters.")
      : undefined);
  const confirmPasswordError =
    getFieldError(passwordMutation.error, "confirmPassword") ??
    (confirmPasswordTouched
      ? !confirmPasswordHasValue
        ? t("This field is required.")
        : !passwordsMatch
          ? t("The passwords do not match.")
          : undefined
      : undefined);
  const canSubmit =
    currentPasswordPresent && newPasswordValid && passwordsMatch &&
    !passwordMutation.isPending;

  const updatePasswordValidity = (field: "currentPassword" | "newPassword" | "confirmPassword") => {
    const currentPassword = currentPasswordInput.current?.value ?? "";
    const newPassword = newPasswordInput.current?.value ?? "";
    const confirmPassword = confirmPasswordInput.current?.value ?? "";
    const nextCurrentPresent = currentPassword.length > 0;
    const nextPasswordValid = newPassword.length >= 8 && newPassword.length <= 72;
    const nextConfirmHasValue = confirmPassword.length > 0;
    const nextPasswordsMatch = nextConfirmHasValue && newPassword === confirmPassword;
    setCurrentPasswordTouched((touched) => touched || Boolean(currentPassword));
    setNewPasswordTouched((touched) => touched || Boolean(newPassword));
    setConfirmPasswordTouched((touched) => touched || nextConfirmHasValue);
    setCurrentPasswordPresent((present) =>
      present === nextCurrentPresent ? present : nextCurrentPresent,
    );
    setNewPasswordValid((valid) => valid === nextPasswordValid ? valid : nextPasswordValid);
    setConfirmPasswordHasValue((hasValue) =>
      hasValue === nextConfirmHasValue ? hasValue : nextConfirmHasValue,
    );
    setPasswordsMatch((matches) =>
      matches === nextPasswordsMatch ? matches : nextPasswordsMatch,
    );
    if (field === "currentPassword") setCurrentPasswordChangedAfterFailure(true);
    if (field === "newPassword") setNewPasswordChangedAfterFailure(true);
    if (errorMessage) setErrorMessage(null);
  };

  const submitPasswordChange = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    updatePasswordValidity("currentPassword");
    updatePasswordValidity("newPassword");
    updatePasswordValidity("confirmPassword");
    const currentPassword = currentPasswordInput.current?.value ?? "";
    const newPassword = newPasswordInput.current?.value ?? "";
    const confirmPassword = confirmPasswordInput.current?.value ?? "";
    if (!currentPassword || newPassword.length < 8 || newPassword.length > 72 ||
        !confirmPassword || newPassword !== confirmPassword) {
      setCurrentPasswordTouched(true);
      setNewPasswordTouched(true);
      setConfirmPasswordTouched(true);
      return;
    }
    setErrorMessage(null);
    setSuccessMessage(null);
    passwordMutation.mutate();
  };

  return (
    <Stack spacing={3}>
      <Box
        sx={{
          p: { xs: 3, md: 4 },
          borderRadius: 4,
          color: "common.white",
          background:
            "linear-gradient(135deg, rgba(11,92,171,0.98) 0%, rgba(18,62,108,0.96) 45%, rgba(10,71,132,0.94) 100%)",
          boxShadow: 3,
        }}
      >
        <Typography variant="overline" sx={{ opacity: 0.8, letterSpacing: 1.5 }}>
          {t("Security")}</Typography>
        <Typography component="h1" variant="h4" fontWeight={800}>
          {t("Change password")}</Typography>
        <Typography sx={{ maxWidth: 760, opacity: 0.9 }}>
          {t("Update the current password for your admin account.")}</Typography>
      </Box>

      <Card sx={{ maxWidth: 760, border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={2.25}>
            <div>
              <Typography variant="h6">{t("Password form")}</Typography>
              <Typography color="text.secondary">{t("Use the current password to verify your identity.")}</Typography>
            </div>
            <Box component="form" onSubmit={submitPasswordChange} noValidate>
              <Stack spacing={2.25}>
                <TextField
                  label={t("Current password")}
                  type={showCurrentPassword ? "text" : "password"}
                  inputRef={currentPasswordInput}
                  onChange={() => updatePasswordValidity("currentPassword")}
                  fullWidth
                  required
                  disabled={passwordMutation.isPending}
                  autoComplete="current-password"
                  error={Boolean(currentPasswordError)}
                  helperText={currentPasswordError ? t(currentPasswordError) : undefined}
                  InputProps={{
                    endAdornment: (
                      <Button
                        onClick={() => setShowCurrentPassword((current) => !current)}
                        size="small"
                        type="button"
                        sx={{ minWidth: 0, px: 1 }}
                      >
                        {showCurrentPassword ? t("Hide") : t("Show")}
                      </Button>
                    ),
                  }}
                />
                <TextField
                  label={t("New password")}
                  type={showNewPassword ? "text" : "password"}
                  inputRef={newPasswordInput}
                  onChange={() => updatePasswordValidity("newPassword")}
                  fullWidth
                  required
                  disabled={passwordMutation.isPending}
                  autoComplete="new-password"
                  error={Boolean(newPasswordError)}
                  helperText={
                    newPasswordError
                      ? t(newPasswordError)
                      : t("Password must contain 8 to 72 characters.")
                  }
                  InputProps={{
                    endAdornment: (
                      <Button
                        onClick={() => setShowNewPassword((current) => !current)}
                        size="small"
                        type="button"
                        sx={{ minWidth: 0, px: 1 }}
                      >
                        {showNewPassword ? t("Hide") : t("Show")}
                      </Button>
                    ),
                  }}
                />
                <TextField
                  label={t("Confirm new password")}
                  type="password"
                  inputRef={confirmPasswordInput}
                  onChange={() => updatePasswordValidity("confirmPassword")}
                  fullWidth
                  required
                  disabled={passwordMutation.isPending}
                  autoComplete="new-password"
                  error={Boolean(confirmPasswordError)}
                  helperText={
                    confirmPasswordError ?? t("Re-enter the new password.")
                  }
                />
                {errorMessage && !currentPasswordError && !newPasswordError &&
                !confirmPasswordError ? (
                  <Alert severity="error">{t(errorMessage)}</Alert>
                ) : null}
                {successMessage ? <Alert severity="success">{t(successMessage)}</Alert> : null}
                <Stack direction="row" spacing={1.5} flexWrap="wrap">
                  <Button
                    variant="contained"
                    type="submit"
                    disabled={!canSubmit}
                  >
                    {passwordMutation.isPending ? t("Saving...") : t("Save password")}
                  </Button>
                  <Button
                    variant="outlined"
                    color="inherit"
                    type="button"
                    onClick={() => {
                      if (currentPasswordInput.current) currentPasswordInput.current.value = "";
                      if (newPasswordInput.current) newPasswordInput.current.value = "";
                      if (confirmPasswordInput.current) confirmPasswordInput.current.value = "";
                      setCurrentPasswordPresent(false);
                      setNewPasswordValid(false);
                      setConfirmPasswordHasValue(false);
                      setPasswordsMatch(false);
                      setCurrentPasswordTouched(false);
                      setNewPasswordTouched(false);
                      setConfirmPasswordTouched(false);
                      passwordMutation.reset();
                      setErrorMessage(null);
                      setSuccessMessage(null);
                    }}
                    disabled={passwordMutation.isPending}
                  >
                    {t("Clear")}
                  </Button>
                </Stack>
                {!canSubmit && !passwordMutation.isPending ? (
                  <Typography variant="body2" color="text.secondary">
                    {t("Complete all required fields and make sure the passwords match.")}
                  </Typography>
                ) : null}
              </Stack>
            </Box>
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  );
}
