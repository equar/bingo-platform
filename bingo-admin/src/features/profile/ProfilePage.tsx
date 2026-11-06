import {
  Alert,
  Avatar,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  FormControl,
  FormHelperText,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { FormEvent, useMemo, useState } from "react";
import { readSession, useSession, writeSession } from "../../api/session";
import { updateMyPreferences, updateMyProfile } from "../../api/admin";
import { useTranslation } from "react-i18next";
import { resolvePreferredLanguage, type SupportedLanguage } from "../../i18n";

export function ProfilePage() {
  const { t, i18n } = useTranslation();
  const session = useSession();
  const [selectedLanguage, setSelectedLanguage] = useState<SupportedLanguage>(() =>
    resolvePreferredLanguage(session?.user.preferredLanguage),
  );
  const [successOpen, setSuccessOpen] = useState(false);
  const [profileSuccessOpen, setProfileSuccessOpen] = useState(false);
  const [displayName, setDisplayName] = useState(session?.user.displayName ?? "");
  const [phoneNumber, setPhoneNumber] = useState(session?.user.phoneNumber ?? "");
  const [nameError, setNameError] = useState(false);
  const [phoneError, setPhoneError] = useState(false);

  const profileMutation = useMutation({
    mutationFn: async () => {
      const token = readSession()?.accessToken;
      const response = await updateMyProfile({ displayName: displayName.trim(), phoneNumber: phoneNumber.trim() });
      return { token, response };
    },
    onSuccess: ({ token, response }) => {
      const latest = readSession();
      if (!latest || !token || latest.accessToken !== token) return;
      writeSession({ ...latest, accessToken: response.accessToken, tokenType: response.tokenType, user: { ...latest.user, ...response.user } });
      setDisplayName(response.user.displayName);
      setPhoneNumber(response.user.phoneNumber ?? "");
      setProfileSuccessOpen(true);
    },
  });

  function submitProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const invalidName = !displayName.trim();
    const invalidPhone = !phoneNumber.trim();
    setNameError(invalidName);
    setPhoneError(invalidPhone);
    if (!invalidName && !invalidPhone) profileMutation.mutate();
  }

  const preferencesMutation = useMutation({
    mutationFn: async (language: SupportedLanguage) => {
      const token = readSession()?.accessToken;
      const user = await updateMyPreferences({ preferredLanguage: language });
      return { token, user };
    },
    onSuccess: ({ token, user }) => {
      const latest = readSession();
      // Ignore late responses that belong to a previous session or account.
      if (!latest || !token || latest.accessToken !== token) {
        return;
      }
      writeSession({ ...latest, user: { ...latest.user, ...user } });
      setSuccessOpen(true);
    },
  });

  const status = session?.user.status ?? "UNKNOWN";
  const statusKey: Record<string, string> = {
    ACTIVE: "status.active",
    INACTIVE: "status.inactive",
    PENDING: "status.pending",
    SUSPENDED: "status.suspended",
    UNKNOWN: "status.unknown",
  };
  const statusLabel = t(statusKey[status] ?? "status.unknown");
  const dateLocale = i18n.language.startsWith("am") ? "am-ET" : "en-US";
  const initials = useMemo(() => session?.user.displayName?.slice(0, 1)?.toUpperCase() ?? "A", [session]);

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
        <Stack direction={{ xs: "column", md: "row" }} spacing={3} alignItems={{ md: "center" }}>
          <Avatar sx={{ width: 72, height: 72, bgcolor: "rgba(255,255,255,0.16)", fontSize: 30 }}>{initials}</Avatar>
          <Stack spacing={1} flexGrow={1}>
            <Typography variant="overline" sx={{ opacity: 0.8, letterSpacing: 1.5 }}>
              {t("profile.adminProfile")}
            </Typography>
            <Typography component="h1" variant="h4" fontWeight={800}>
              {t("profile.title")}
            </Typography>
            <Typography sx={{ maxWidth: 760, opacity: 0.9 }}>
              {t("profile.intro")}
            </Typography>
          </Stack>
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {session?.user.roles.map((role) => (
              <Chip
                key={role}
                label={role}
                sx={{ color: "common.white", borderColor: "rgba(255,255,255,0.35)" }}
                variant="outlined"
              />
            ))}
          </Stack>
        </Stack>
      </Box>

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={7}>
          <Card sx={{ border: "1px solid", borderColor: "divider" }}>
            <CardContent>
              <Stack spacing={2.25}>
                <div>
                  <Typography variant="h6" component="h2">{t("profile.personalInfo")}</Typography>
                  <Typography color="text.secondary">{t("profile.personalInfoIntro")}</Typography>
                </div>
                <Box component="form" onSubmit={submitProfile} noValidate>
                  <Stack spacing={2}>
                    <TextField
                      fullWidth required label={t("profile.displayName")}
                      value={displayName} onChange={(event) => setDisplayName(event.target.value)}
                      error={nameError} helperText={nameError ? t("profile.nameRequired") : undefined}
                      autoComplete="name" inputProps={{ maxLength: 120 }}
                    />
                    <TextField
                      fullWidth label={t("profile.email")} value={session?.user.email ?? ""}
                      InputProps={{ readOnly: true }}
                      helperText={t("profile.emailReadOnly")}
                      autoComplete="email"
                    />
                    <TextField
                      fullWidth required label={t("profile.phone")} value={phoneNumber}
                      onChange={(event) => { setPhoneNumber(event.target.value); setPhoneError(false); }}
                      error={phoneError} helperText={phoneError ? t("This field is required.") : t("profile.phoneHelper")} autoComplete="tel"
                      inputProps={{ inputMode: "tel", maxLength: 20 }}
                    />
                    {profileMutation.isError ? <Alert severity="error">{t("profile.saveFailed")}</Alert> : null}
                    <Box>
                      <Button type="submit" variant="contained" disabled={profileMutation.isPending}
                        aria-busy={profileMutation.isPending}>
                        {profileMutation.isPending ? t("profile.saving") : t("profile.saveChanges")}
                      </Button>
                    </Box>
                  </Stack>
                </Box>
                <Divider />
                <Stack spacing={1.5}>
                  <DetailRow
                    label={t("profile.status")}
                    value={
                      <Chip label={statusLabel} size="small" color="success" variant="outlined" />
                    }
                  />
                  <DetailRow
                    label={t("profile.roles")}
                    value={
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        {session?.user.roles.map((role) => (
                          <Chip key={role} label={role} size="small" />
                        ))}
                      </Stack>
                    }
                  />
                  <DetailRow label={t("profile.userId")} value={<Typography>{session?.user.id ?? "-"}</Typography>} />
                  <DetailRow
                    label={t("profile.created")}
                    value={<Typography>{session ? new Date(session.user.createdAt).toLocaleString(dateLocale) : "-"}</Typography>}
                  />
                  <DetailRow
                    label={t("profile.updated")}
                    value={<Typography>{session ? new Date(session.user.updatedAt).toLocaleString(dateLocale) : "-"}</Typography>}
                  />
                </Stack>
                <Divider />
                <Typography color="text.secondary">
                  {t("profile.accountMenuHint")}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} lg={5}>
          <Card
            component="section"
            aria-labelledby="profile-preferences-heading"
            sx={{ border: "1px solid", borderColor: "divider" }}
          >
            <CardContent>
              <Stack spacing={2.25}>
                <div>
                  <Typography variant="h6" component="h2" id="profile-preferences-heading">
                    {t("profile.preferences")}
                  </Typography>
                  <Typography color="text.secondary">{t("profile.preferencesIntro")}</Typography>
                </div>
                <FormControl fullWidth>
                  <InputLabel id="profile-language-label">{t("language.label")}</InputLabel>
                  <Select
                    labelId="profile-language-label"
                    id="profile-language-select"
                    value={selectedLanguage}
                    label={t("language.label")}
                    onChange={(event) =>
                      setSelectedLanguage(resolvePreferredLanguage(event.target.value))
                    }
                  >
                    <MenuItem value="en">English</MenuItem>
                    <MenuItem value="am">አማርኛ</MenuItem>
                  </Select>
                  <FormHelperText>{t("language.helper")}</FormHelperText>
                </FormControl>
                {preferencesMutation.isError ? (
                  <Alert severity="error">{t("language.saveFailed")}</Alert>
                ) : null}
                <Box>
                  <Button
                    variant="contained"
                    onClick={() => preferencesMutation.mutate(selectedLanguage)}
                    disabled={preferencesMutation.isPending}
                    aria-busy={preferencesMutation.isPending}
                  >
                    {preferencesMutation.isPending ? t("language.saving") : t("Save")}
                  </Button>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      <Snackbar open={successOpen} autoHideDuration={5000} onClose={() => setSuccessOpen(false)}>
        <Alert severity="success" onClose={() => setSuccessOpen(false)}>
          {t("language.saved")}
        </Alert>
      </Snackbar>
      <Snackbar open={profileSuccessOpen} autoHideDuration={5000} onClose={() => setProfileSuccessOpen(false)}>
        <Alert severity="success" onClose={() => setProfileSuccessOpen(false)}>{t("profile.saved")}</Alert>
      </Snackbar>
    </Stack>
  );
}

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
      <Typography color="text.secondary">{label}</Typography>
      {value}
    </Box>
  );
}
