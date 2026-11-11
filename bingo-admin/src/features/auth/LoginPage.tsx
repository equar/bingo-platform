import { Alert, Box, Button, Container, InputAdornment, Paper, Stack, TextField, Typography } from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { login } from "../../api/admin";
import { clearSession, consumeSessionExpired, writeSession } from "../../api/session";
import { parseApiError } from "../../api/types";
import i18n from "../../i18n";

const LOGIN_EMAIL_KEY = "bingo-admin-login-email";

export function LoginPage() {
  // The login screen always renders in English, regardless of the browser
  // language or any previously signed-in account's preference.
  const t = i18n.getFixedT("en");
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState(() => window.localStorage.getItem(LOGIN_EMAIL_KEY) ?? "");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);

  useEffect(() => {
    if (consumeSessionExpired()) setSessionExpired(true);
  }, []);

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
  const accountActivated =
    typeof location.state === "object" &&
    location.state !== null &&
    "accountActivated" in location.state &&
    location.state.accountActivated === true;

  const mutation = useMutation({
    mutationFn: () => login({ email, password }),
    onSuccess: (response) => {
      writeSession({
        accessToken: response.accessToken,
        tokenType: response.tokenType,
        user: response.user,
      });
      navigate(response.user.mustChangePassword ? "/change-password" : redirectTo, { replace: true });
    },
    onError: (error) => {
      const parsed = parseApiError(error);
      if (parsed.cancelled) {
        setErrorMessage(null);
      } else if (parsed.status === 0) {
        setErrorMessage("login.noConnection");
      } else if (parsed.status === 503) {
        setErrorMessage("login.serviceUnavailable");
      } else if (parsed.status === 401 || parsed.status === 403 || parsed.code === "AUTH_INVALID_CREDENTIALS") {
        setErrorMessage("login.invalidCredentials");
      } else {
        setErrorMessage("login.unexpected");
      }
      clearSession();
    },
  });

  return (
    <Container
      maxWidth={false}
      sx={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        px: 2,
        py: { xs: 4, sm: 6 },
        bgcolor: "background.default",
      }}
    >
      <Paper
        elevation={0}
        sx={{
          position: "relative",
          zIndex: 1,
          width: "100%",
          maxWidth: 440,
          p: { xs: 3, sm: 4.5 },
          borderRadius: 4,
          border: "1px solid",
          borderColor: "divider",
          backgroundColor: "background.paper",
          boxShadow: "0 18px 48px rgba(23, 36, 59, 0.08), 0 2px 8px rgba(23, 36, 59, 0.03)",
        }}
      >
        <Stack spacing={3}>
          {accountActivated ? <Alert severity="success">{t("activation.success")}</Alert> : null}
          {sessionExpired ? <Alert severity="info">{t("login.sessionExpired")}</Alert> : null}
          <Stack spacing={2} alignItems="center" textAlign="center">
            <Box
              sx={{
                width: 58,
                height: 58,
                display: "grid",
                placeItems: "center",
                borderRadius: 3,
                bgcolor: "primary.main",
                boxShadow: "0 8px 18px rgba(40, 87, 164, 0.2)",
              }}
            >
              <svg width="32" height="32" viewBox="0 0 40 40" fill="none" aria-hidden="true" focusable="false">
                <path
                  d="M11 23a9 9 0 0 1 18 0M20 5v4M8.7 10.7l2.8 2.8M5 23h4M31 23h4M28.5 13.5l2.8-2.8"
                  stroke="white"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                />
                <path d="M7 25h26M11 30h18" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
              </svg>
            </Box>
            <Box>
              <Typography
                variant="overline"
                sx={{ color: "primary.main", fontWeight: 750, letterSpacing: "0.12em", lineHeight: 1.8 }}
              >
                {t("login.adminConsole")}
              </Typography>
              <Typography variant="h4" sx={{ mt: 0.25, color: "text.primary" }}>
                {t("brand")}
              </Typography>
            </Box>
            <Typography color="text.secondary" sx={{ maxWidth: 360, lineHeight: 1.6 }}>
              {t("login.welcome")}
            </Typography>
          </Stack>
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
              label={t("login.email")}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              name="email"
              autoComplete="username"
              fullWidth
              required
            />
            <TextField
              label={t("login.password")}
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
                      aria-label={showPassword ? t("login.hidePassword") : t("login.showPassword")}
                      color="inherit"
                      size="small"
                      sx={{ minWidth: 0, px: 1 }}
                      type="button"
                    >
                      {showPassword ? t("login.hide") : t("login.show")}
                    </Button>
                  </InputAdornment>
                ),
              }}
            />
            {errorMessage ? <Alert severity="error">{t(errorMessage)}</Alert> : null}
            <Button
              type="submit"
              variant="contained"
              size="large"
              disabled={mutation.isPending}
              sx={{
                minHeight: 48,
                borderRadius: 2.5,
              }}
            >
              {mutation.isPending ? t("login.signingIn") : t("login.signIn")}
            </Button>
          </Stack>
          <Typography variant="caption" color="text.secondary" textAlign="center">
            {t("login.secureAccess")}
          </Typography>
        </Stack>
      </Paper>
    </Container>
  );
}
