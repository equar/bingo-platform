import { Alert, Button, Container, InputAdornment, Paper, Stack, TextField, Typography } from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { login } from "../../api/admin";
import { clearSession, writeSession } from "../../api/session";
import { parseApiError } from "../../api/types";

const LOGIN_EMAIL_KEY = "bingo-admin-login-email";

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState(() => window.localStorage.getItem(LOGIN_EMAIL_KEY) ?? "");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (email.trim()) {
      window.localStorage.setItem(LOGIN_EMAIL_KEY, email.trim());
    } else {
      window.localStorage.removeItem(LOGIN_EMAIL_KEY);
    }
  }, [email]);

  const redirectTo = useMemo(() => {
    return typeof location.state === "object" && location.state && "from" in location.state
      ? String((location.state as { from?: unknown }).from ?? "/dashboard")
      : "/dashboard";
  }, [location.state]);

  const mutation = useMutation({
    mutationFn: () => login({ email, password }),
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
              label="Email address"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              name="email"
              autoComplete="username"
              fullWidth
              required
            />
            <TextField
              label="Password"
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              name="password"
              autoComplete="current-password"
              fullWidth
              required
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <Button
                      onClick={() => setShowPassword((current) => !current)}
                      aria-label={showPassword ? "Hide password" : "Show password"}
                      color="inherit"
                      size="small"
                      sx={{ minWidth: 0, px: 1 }}
                      type="button"
                    >
                      {showPassword ? "Hide" : "Show"}
                    </Button>
                  </InputAdornment>
                ),
              }}
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
