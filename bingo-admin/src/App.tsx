import { Alert, Container, CssBaseline, Stack, Typography } from "@mui/material";
import { QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { apiBaseUrl } from "./api/http";
import { queryClient } from "./api/queryClient";
import { AppRouter } from "./routes/AppRouter";
import { adminTheme } from "./theme";
import { ThemeProvider } from "@mui/material/styles";

function MissingConfig() {
  return (
    <Container maxWidth="sm" sx={{ py: 8 }}>
      <Stack spacing={2}>
        <Typography variant="h4">Sunset Bingo Admin</Typography>
        <Alert severity="warning">
          Missing Vite environment variable <strong>VITE_API_BASE_URL</strong>.
        </Alert>
        <Typography color="text.secondary">
          Configure the backend base URL in <code>.env.local</code> before starting the admin console.
        </Typography>
      </Stack>
    </Container>
  );
}

export default function App() {
  return (
    <ThemeProvider theme={adminTheme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          {apiBaseUrl ? <AppRouter /> : <MissingConfig />}
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
