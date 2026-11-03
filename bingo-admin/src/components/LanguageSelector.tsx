import { Alert, FormControl, MenuItem, Select, Snackbar, useMediaQuery } from "@mui/material";
import type { SelectChangeEvent } from "@mui/material";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";

export function LanguageSelector() {
  const { t } = useTranslation();
  const [saveFailed, setSaveFailed] = useState(false);
  const language = i18n.resolvedLanguage === "am" || i18n.language.startsWith("am") ? "am" : "en";
  const compact = useMediaQuery("(max-width:599px)");

  const handleLanguageChange = async (event: SelectChangeEvent) => {
    const nextLanguage = event.target.value as "en" | "am";
    const previousLanguage = language;
    try {
      await i18n.changeLanguage(nextLanguage);
      window.localStorage.setItem("bingo-admin-locale", nextLanguage);
      setSaveFailed(false);
    } catch (error) {
      console.error("Could not save the admin language preference.", error);
      await i18n.changeLanguage(previousLanguage);
      setSaveFailed(true);
    }
  };

  return (
    <>
      <FormControl size="small" sx={{ minWidth: compact ? 72 : 120 }}>
        <Select
          value={language}
          onChange={handleLanguageChange}
          inputProps={{ "aria-label": t("language.label") }}
          renderValue={(value) => compact ? (value === "am" ? "አማ" : "EN") : value === "am" ? "አማርኛ" : "English"}
          sx={{
            color: "inherit",
            ".MuiOutlinedInput-notchedOutline": { borderColor: "currentColor" },
            ".MuiSelect-select": { px: compact ? 1 : 1.5 },
          }}
        >
          <MenuItem value="en">{t("English")}</MenuItem>
          <MenuItem value="am">{t("አማርኛ")}</MenuItem>
        </Select>
      </FormControl>
      <Snackbar open={saveFailed} autoHideDuration={5000} onClose={() => setSaveFailed(false)}>
        <Alert severity="error" onClose={() => setSaveFailed(false)}>
          {t("language.saveFailed")}
        </Alert>
      </Snackbar>
    </>
  );
}
