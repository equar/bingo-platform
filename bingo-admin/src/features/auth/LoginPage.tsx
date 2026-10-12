import { Button, Container, Paper, Stack, TextField, Typography, Alert } from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { login } from "../../api/admin";
import { clearSession, writeSession } from "../../api/session";
import { parseApiError } from "../../api/types";

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [phoneNumber, setPhoneNumber] = useState("");
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const redirectTo = useMemo(() => {
    return typeof location.state === "object" && location.state && "from" in location.state
      ? String((location.state as { from?: unknown }).from ?? "/dashboard")
      : "/dashboard";
  }, [location.state]);

  const mutation = useMutation({
    mutationFn: () => login({ phoneNumber, password }),
    onSuccess: (response) => {
      writeSession({
        accessToken: response.accessToken,
        tokenType: response.tokenType,
        user: response.user,
      });
      navigate(redirectTo, { replace: true });
    },
    onError: (error) => {
      const parsed = parseApiError(error);
      setErrorMessage(`${parsed.code ?? "AUTH_FAILED"}: ${parsed.message}`);
      clearSession();
    },
  });

  return (
    <Container maxWidth="sm" sx={{ py: 8 }}>
      <Paper elevation={3} sx={{ p: 4, borderRadius: 4 }}>
        <Stack spacing={3}>
          <div>
            <Typography variant="h4" fontWeight={700} gutterBottom>
              Bingo Admin
            </Typography>
            <Typography color="text.secondary">
              Sign in with an admin or support account to access the console.
            </Typography>
          </div>
          <Stack
            spacing={2}
            component="form"
            onSubmit={(event) => {
              event.preventDefault();
              setErrorMessage(null);
              mutation.mutate();
            }}
          >
            <TextField
              label="Phone number"
              value={phoneNumber}
              onChange={(event) => setPhoneNumber(event.target.value)}
              autoComplete="username"
              fullWidth
              required
            />
            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              fullWidth
              required
            />
            {errorMessage ? <Alert severity="error">{errorMessage}</Alert> : null}
            <Button type="submit" variant="contained" size="large" disabled={mutation.isPending}>
              {mutation.isPending ? "Signing in..." : "Sign in"}
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Container>
  );
}
