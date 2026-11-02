import { Alert, Button, Container, Paper, Stack, TextField, Typography } from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { activateAdminAccount } from "../../api/admin";
import { getFieldError, parseApiError } from "../../api/types";

export function AdminAccountActivationPage() {
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
      setSuccessMessage("Your account is ready. Sign in with your new password.");
      setErrorMessage(null);
      setNewPassword("");
      setConfirmPassword("");
      navigate("/activate-account", { replace: true });
    },
    onError: (error) => {
      const parsed = parseApiError(error);
      setErrorMessage(parsed.message);
      setSuccessMessage(null);
    },
  });
  const newPasswordError = getFieldError(activationMutation.error, "newPassword");
  const confirmationError = newPassword !== confirmPassword && Boolean(confirmPassword)
    ? "The passwords do not match."
    : getFieldError(activationMutation.error, "confirmPassword");

  useEffect(() => {
    if (token && window.location.hash) {
      window.history.replaceState(window.history.state, document.title, `${location.pathname}${location.search}`);
    }
  }, [location.pathname, location.search, token]);

  return (
    <Container maxWidth="sm" sx={{ py: 8 }}>
      <Paper elevation={3} sx={{ p: 4, borderRadius: 4 }}>
        <Stack spacing={3}>
          <div>
            <Typography variant="h4" fontWeight={700} gutterBottom>
              Set up your Sunset Bingo Admin account
            </Typography>
            <Typography color="text.secondary">
              Choose a password to activate your account. The activation link can only be used once.
            </Typography>
          </div>
          {!token && !successMessage ? (
            <Alert severity="error">This activation link is missing or has already been used.</Alert>
          ) : null}
          <TextField
            label="New password"
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            autoComplete="new-password"
            error={Boolean(newPasswordError)}
            helperText={newPasswordError ?? "Use at least 8 characters."}
            required
            fullWidth
            disabled={!token || activationMutation.isPending || Boolean(successMessage)}
          />
          <TextField
            label="Confirm new password"
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
            <Alert severity="error">{errorMessage}</Alert>
          ) : null}
          {successMessage ? <Alert severity="success">{successMessage}</Alert> : null}
          {successMessage ? (
            <Button variant="contained" onClick={() => navigate("/login")}>
              Sign in
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
              {activationMutation.isPending ? "Activating..." : "Activate account"}
            </Button>
          )}
        </Stack>
      </Paper>
    </Container>
  );
}
