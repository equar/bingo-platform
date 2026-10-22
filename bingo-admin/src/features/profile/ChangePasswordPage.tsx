import { Alert, Box, Button, Card, CardContent, Stack, TextField, Typography } from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { changeAdminPassword } from "../../api/admin";
import { readSession, writeSession } from "../../api/session";
import { parseApiError } from "../../api/types";

export function ChangePasswordPage() {
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const passwordMutation = useMutation({
    mutationFn: () => changeAdminPassword({ currentPassword, newPassword }),
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
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setShowCurrentPassword(false);
      setShowNewPassword(false);
      navigate("/dashboard", { replace: true });
    },
    onError: (error) => {
      const parsed = parseApiError(error);
      setErrorMessage(`${parsed.code ?? "PASSWORD_UPDATE_FAILED"}: ${parsed.message}`);
      setSuccessMessage(null);
    },
  });

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
          Security
        </Typography>
        <Typography component="h1" variant="h4" fontWeight={800}>
          Change password
        </Typography>
        <Typography sx={{ maxWidth: 760, opacity: 0.9 }}>
          Update the current password for your admin account.
        </Typography>
      </Box>

      <Card sx={{ maxWidth: 760, border: "1px solid", borderColor: "divider" }}>
        <CardContent>
          <Stack spacing={2.25}>
            <div>
              <Typography variant="h6">Password form</Typography>
              <Typography color="text.secondary">Use the current password to verify your identity.</Typography>
            </div>
            <TextField
              label="Current password"
              type={showCurrentPassword ? "text" : "password"}
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              fullWidth
              required
              autoComplete="current-password"
              InputProps={{
                endAdornment: (
                  <Button
                    onClick={() => setShowCurrentPassword((current) => !current)}
                    size="small"
                    type="button"
                    sx={{ minWidth: 0, px: 1 }}
                  >
                    {showCurrentPassword ? "Hide" : "Show"}
                  </Button>
                ),
              }}
            />
            <TextField
              label="New password"
              type={showNewPassword ? "text" : "password"}
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              fullWidth
              required
              autoComplete="new-password"
              helperText="Use at least 8 characters."
              InputProps={{
                endAdornment: (
                  <Button
                    onClick={() => setShowNewPassword((current) => !current)}
                    size="small"
                    type="button"
                    sx={{ minWidth: 0, px: 1 }}
                  >
                    {showNewPassword ? "Hide" : "Show"}
                  </Button>
                ),
              }}
            />
            <TextField
              label="Confirm new password"
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              fullWidth
              required
              autoComplete="new-password"
            />
            {errorMessage ? <Alert severity="error">{errorMessage}</Alert> : null}
            {successMessage ? <Alert severity="success">{successMessage}</Alert> : null}
            <Stack direction="row" spacing={1.5} flexWrap="wrap">
              <Button
                variant="contained"
                onClick={() => {
                  if (newPassword !== confirmPassword) {
                    setErrorMessage("PASSWORD_CONFIRMATION_MISMATCH: New passwords do not match");
                    setSuccessMessage(null);
                    return;
                  }

                  setErrorMessage(null);
                  setSuccessMessage(null);
                  passwordMutation.mutate();
                }}
                disabled={passwordMutation.isPending}
              >
                {passwordMutation.isPending ? "Saving..." : "Save password"}
              </Button>
              <Button
                variant="outlined"
                color="inherit"
                onClick={() => {
                  setCurrentPassword("");
                  setNewPassword("");
                  setConfirmPassword("");
                  setErrorMessage(null);
                  setSuccessMessage(null);
                }}
                disabled={passwordMutation.isPending}
              >
                Clear
              </Button>
              <Button variant="text" color="inherit" onClick={() => navigate("/profile")}>
                Back to profile
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  );
}
