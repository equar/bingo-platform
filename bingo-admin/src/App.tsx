import { Alert, Container, CssBaseline, Stack, Typography } from "@mui/material";
import { QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { apiBaseUrl } from "./api/http";
import { queryClient } from "./api/queryClient";
import { AppRouter } from "./routes/AppRouter";
import { adminTheme } from "./theme";
import { ThemeProvider } from "@mui/material/styles";
import { I18nextProvider, useTranslation } from "react-i18next";
import i18n from "./i18n";

function MissingConfig() {
  const { t } = useTranslation();
  return (
    <Container maxWidth="sm" sx={{ py: 8 }}>
      <Stack spacing={2}>
        <Typography variant="h4">{t("missingConfig.title")}</Typography>
        <Alert severity="warning">
          {t("missingConfig.alert")} <strong>{t("VITE_API_BASE_URL")}</strong>{t(".")}</Alert>
        <Typography color="text.secondary">
          {t("missingConfig.body")}
        </Typography>
      </Stack>
    </Container>
  );
}

export default function App() {
  return (
    <ThemeProvider theme={adminTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            {apiBaseUrl ? <AppRouter /> : <MissingConfig />}
          </BrowserRouter>
        </QueryClientProvider>
      </I18nextProvider>
    </ThemeProvider>
  );
}
