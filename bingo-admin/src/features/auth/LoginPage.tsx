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

  const mutation = useMutation({
    mutationFn: () => login({ email, password }),
    onSuccess: (response) => {
      writeSession({
        accessToken: response.accessToken,
        tokenType: response.tokenType,
        user: response.user,
      });
      const supportOnly = response.user.roles.includes("SUPPORT") && !response.user.roles.includes("ADMIN");
      const destination = supportOnly && redirectTo === "/dashboard" ? "/support/tickets" : redirectTo;
      navigate(response.user.mustChangePassword ? "/change-password" : destination, { replace: true });
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
        position: "relative",
        overflow: "hidden",
        px: 2,
        py: { xs: 4, sm: 6 },
        background:
          "radial-gradient(ellipse at 15% 15%, rgba(255, 194, 137, 0.28), transparent 42%), radial-gradient(ellipse at 85% 85%, rgba(226, 105, 83, 0.12), transparent 38%), linear-gradient(135deg, #fffaf5 0%, #f7f7fb 54%, #fff5ee 100%)",
      }}
    >
      <Box
        aria-hidden="true"
        sx={{
          position: "absolute",
          width: { xs: 240, sm: 340 },
          height: { xs: 240, sm: 340 },
          borderRadius: "50%",
          border: "1px solid rgba(217, 119, 87, 0.12)",
          top: { xs: -130, sm: -170 },
          right: { xs: -120, sm: "8%" },
        }}
      />
      <Box
        aria-hidden="true"
        sx={{
          position: "absolute",
          width: { xs: 180, sm: 260 },
          height: { xs: 180, sm: 260 },
          borderRadius: "50%",
          border: "1px solid rgba(217, 119, 87, 0.1)",
          bottom: { xs: -100, sm: -130 },
          left: { xs: -90, sm: "10%" },
        }}
      />
      <Paper
        elevation={0}
        sx={{
          position: "relative",
          zIndex: 1,
          width: "100%",
          maxWidth: 440,
          p: { xs: 3, sm: 4.5 },
          borderRadius: 5,
          border: "1px solid rgba(86, 60, 48, 0.09)",
          backgroundColor: "rgba(255, 255, 255, 0.94)",
          boxShadow: "0 24px 70px rgba(78, 47, 33, 0.12), 0 3px 10px rgba(78, 47, 33, 0.04)",
          backdropFilter: "blur(12px)",
        }}
      >
        <Stack spacing={3.5}>
          {sessionExpired ? <Alert severity="info">{t("login.sessionExpired")}</Alert> : null}
          <Stack spacing={2} alignItems="center" textAlign="center">
            <Box
              sx={{
                width: 58,
                height: 58,
                display: "grid",
                placeItems: "center",
                borderRadius: "19px",
                background: "linear-gradient(145deg, #f6a15a 0%, #e85f49 100%)",
                boxShadow: "0 10px 24px rgba(226, 105, 75, 0.28)",
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
                sx={{ color: "#b64f32", fontWeight: 800, letterSpacing: "0.16em", lineHeight: 1.8 }}
              >
                {t("login.adminConsole")}
              </Typography>
              <Typography variant="h4" fontWeight={800} sx={{ mt: 0.25, color: "#252638" }}>
                {t("brand")}
              </Typography>
            </Box>
            <Typography color="text.secondary" sx={{ maxWidth: 340, lineHeight: 1.65 }}>
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
              sx={{ "& .MuiOutlinedInput-root": { borderRadius: 2.5, backgroundColor: "#fff" } }}
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
              sx={{ "& .MuiOutlinedInput-root": { borderRadius: 2.5, backgroundColor: "#fff" } }}
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
                background: "linear-gradient(100deg, #da653f 0%, #c84c3d 100%)",
                boxShadow: "0 8px 18px rgba(200, 76, 61, 0.2)",
                textTransform: "none",
                fontWeight: 700,
                "&:hover": {
                  background: "linear-gradient(100deg, #c95734 0%, #b84335 100%)",
                  boxShadow: "0 10px 22px rgba(200, 76, 61, 0.25)",
                },
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
