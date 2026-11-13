import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Box, Button, Card, CardContent, FormControlLabel, Stack, Switch, TextField, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import {
  createPlayerAnnouncement,
  deletePlayerAnnouncement,
  playerAnnouncements,
  playerAppSettings,
  savePlayerAppSettings,
  updatePlayerAnnouncement,
  type PlayerAnnouncementRequest,
  type PlayerAnnouncementResponse,
  type PlayerAppSettingsRequest,
} from "../../api/admin";
import { getUserFacingErrorMessage } from "../../api/types";
import { AdminPageHeader } from "../../components/AdminPageHeader";

const emptySettings: PlayerAppSettingsRequest = {
  bannerTitleEn: "", bannerTitleAm: "", bannerBodyEn: "", bannerBodyAm: "",
  bannerImageUrl: "", termsUrl: "", telegramUrl: "", tiktokUrl: "", supportPhoneNumbers: "",
};

const emptyAnnouncement: PlayerAnnouncementRequest = {
  titleEn: "", titleAm: "", bodyEn: "", bodyAm: "", published: false,
};

export function PlayerAppPage() {
  const { t } = useTranslation();
  const client = useQueryClient();
  const settingsQuery = useQuery({ queryKey: ["player-app-settings"], queryFn: playerAppSettings });
  const messagesQuery = useQuery({ queryKey: ["player-app-messages"], queryFn: playerAnnouncements });
  const [settings, setSettings] = useState(emptySettings);
  const [message, setMessage] = useState(emptyAnnouncement);
  const [editingId, setEditingId] = useState<number | null>(null);

  useEffect(() => {
    if (settingsQuery.data) setSettings({
      bannerTitleEn: settingsQuery.data.bannerTitleEn ?? "",
      bannerTitleAm: settingsQuery.data.bannerTitleAm ?? "",
      bannerBodyEn: settingsQuery.data.bannerBodyEn ?? "",
      bannerBodyAm: settingsQuery.data.bannerBodyAm ?? "",
      bannerImageUrl: settingsQuery.data.bannerImageUrl ?? "",
      termsUrl: settingsQuery.data.termsUrl ?? "",
      telegramUrl: settingsQuery.data.telegramUrl ?? "",
      tiktokUrl: settingsQuery.data.tiktokUrl ?? "",
      supportPhoneNumbers: settingsQuery.data.supportPhoneNumbers ?? "",
    });
  }, [settingsQuery.data]);

  const saveSettings = useMutation({
    mutationFn: () => savePlayerAppSettings(settings),
    onSuccess: async () => client.invalidateQueries({ queryKey: ["player-app-settings"] }),
  });
  const saveMessage = useMutation({
    mutationFn: () => editingId === null ? createPlayerAnnouncement(message) : updatePlayerAnnouncement(editingId, message),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["player-app-messages"] });
      setMessage(emptyAnnouncement);
      setEditingId(null);
    },
  });
  const removeMessage = useMutation({
    mutationFn: deletePlayerAnnouncement,
    onSuccess: () => client.invalidateQueries({ queryKey: ["player-app-messages"] }),
  });

  const updateSettingsField = (field: keyof PlayerAppSettingsRequest, value: string) =>
    setSettings(current => ({ ...current, [field]: value }));
  const updateMessageField = (field: keyof PlayerAnnouncementRequest, value: string | boolean) =>
    setMessage(current => ({ ...current, [field]: value }));
  const edit = (item: PlayerAnnouncementResponse) => {
    setEditingId(item.id);
    setMessage({ titleEn: item.titleEn, titleAm: item.titleAm ?? "", bodyEn: item.bodyEn, bodyAm: item.bodyAm ?? "", published: item.published });
  };
  const errors = [settingsQuery.error, messagesQuery.error, saveSettings.error, saveMessage.error, removeMessage.error]
    .filter(Boolean)
    .map(error => getUserFacingErrorMessage(error) ?? t("Something went wrong. Please try again."));

  return <Box>
    <AdminPageHeader title={t("Player app content")} description={t("Manage the banner, contact links, legal information, and player announcements.")} />
    {errors.map((error, index) => <Alert key={`${index}-${error}`} severity="error" sx={{ mb: 2 }}>{t(error)}</Alert>)}

    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>{t("Player app information")}</Typography>
        <Stack spacing={2}>
          <TextField label={t("Banner title (English)")} value={settings.bannerTitleEn} onChange={event => updateSettingsField("bannerTitleEn", event.target.value)} />
          <TextField label={t("Banner title (Amharic)")} value={settings.bannerTitleAm} onChange={event => updateSettingsField("bannerTitleAm", event.target.value)} />
          <TextField multiline minRows={2} label={t("Banner message (English)")} value={settings.bannerBodyEn} onChange={event => updateSettingsField("bannerBodyEn", event.target.value)} />
          <TextField multiline minRows={2} label={t("Banner message (Amharic)")} value={settings.bannerBodyAm} onChange={event => updateSettingsField("bannerBodyAm", event.target.value)} />
          <TextField label={t("Banner image URL")} value={settings.bannerImageUrl} onChange={event => updateSettingsField("bannerImageUrl", event.target.value)} />
          <TextField label={t("Terms and conditions URL")} value={settings.termsUrl} onChange={event => updateSettingsField("termsUrl", event.target.value)} />
          <TextField label={t("Telegram URL")} value={settings.telegramUrl} onChange={event => updateSettingsField("telegramUrl", event.target.value)} />
          <TextField label={t("TikTok URL")} value={settings.tiktokUrl} onChange={event => updateSettingsField("tiktokUrl", event.target.value)} />
          <TextField label={t("Support phone numbers")} helperText={t("Separate multiple numbers with commas.")} value={settings.supportPhoneNumbers} onChange={event => updateSettingsField("supportPhoneNumbers", event.target.value)} />
          <Box><Button variant="contained" disabled={saveSettings.isPending} onClick={() => saveSettings.mutate()}>{t("Save player app information")}</Button></Box>
        </Stack>
      </CardContent>
    </Card>

    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>{t("Player messages and announcements")}</Typography>
        <Typography color="text.secondary" sx={{ mb: 2 }}>{t("Published messages appear in the player app inbox. Drafts are visible only to administrators.")}</Typography>
        <Stack spacing={2} sx={{ mb: 3 }}>
          <TextField label={t("Title (English)")} value={message.titleEn} onChange={event => updateMessageField("titleEn", event.target.value)} />
          <TextField label={t("Title (Amharic)")} value={message.titleAm} onChange={event => updateMessageField("titleAm", event.target.value)} />
          <TextField multiline minRows={3} label={t("Message (English)")} value={message.bodyEn} onChange={event => updateMessageField("bodyEn", event.target.value)} />
          <TextField multiline minRows={3} label={t("Message (Amharic)")} value={message.bodyAm} onChange={event => updateMessageField("bodyAm", event.target.value)} />
          <FormControlLabel control={<Switch checked={message.published} onChange={event => updateMessageField("published", event.target.checked)} />} label={t("Publish to players")} />
          <Stack direction="row" spacing={1}>
            <Button variant="contained" disabled={saveMessage.isPending} onClick={() => saveMessage.mutate()}>{editingId === null ? t("Create message") : t("Save message")}</Button>
            {editingId !== null && <Button onClick={() => { setEditingId(null); setMessage(emptyAnnouncement); }}>{t("Cancel edit")}</Button>}
          </Stack>
        </Stack>
        <Stack spacing={1}>
          {(messagesQuery.data ?? []).map(item => <Card variant="outlined" key={item.id}>
            <CardContent sx={{ display: "flex", alignItems: "center", gap: 2 }}>
              <Box sx={{ flex: 1 }}>
                <Typography fontWeight={700}>{item.titleEn}</Typography>
                <Typography variant="body2" color="text.secondary">{item.published ? t("Published") : t("Draft")} · {new Date(item.updatedAt).toLocaleString()}</Typography>
              </Box>
              <Button onClick={() => edit(item)}>{t("Edit")}</Button>
              <Button color="error" onClick={() => removeMessage.mutate(item.id)}>{t("Delete")}</Button>
            </CardContent>
          </Card>)}
          {messagesQuery.data?.length === 0 && <Typography color="text.secondary">{t("No player messages yet.")}</Typography>}
        </Stack>
      </CardContent>
    </Card>
  </Box>;
}
