import { Alert, Button, CircularProgress, IconButton, SvgIcon, Tooltip, useMediaQuery, useTheme } from "@mui/material";
import { useTranslation } from "react-i18next";
import { useEffect, useRef } from "react";
import { getUserFacingErrorMessage } from "../api/types";

interface ListRefreshButtonProps {
  refreshing: boolean;
  onRefresh: () => unknown;
  label?: string;
}

export function ListRefreshButton({ refreshing, onRefresh, label }: ListRefreshButtonProps) {
  const { t } = useTranslation();
  const buttonRef = useRef<HTMLButtonElement>(null);
  const restoreFocus = useRef(false);
  const wasRefreshing = useRef(refreshing);
  const theme = useTheme();
  const narrow = useMediaQuery(theme.breakpoints.down("sm"));
  const text = t(label ?? "Refresh");
  const icon = refreshing ? <CircularProgress size={18} aria-label={t("Refreshing")} /> : (
    <SvgIcon fontSize="small" aria-hidden="true"><path d="M17.65 6.35A7.95 7.95 0 0 0 12 4a8 8 0 1 0 7.93 9h-2.02A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4z" /></SvgIcon>
  );
  const handleRefresh = () => {
    restoreFocus.current = buttonRef.current === document.activeElement;
    void onRefresh();
  };
  useEffect(() => {
    if (refreshing) {
      wasRefreshing.current = true;
    } else if (wasRefreshing.current && restoreFocus.current) {
      buttonRef.current?.focus();
      restoreFocus.current = false;
    }
    if (!refreshing) wasRefreshing.current = false;
  }, [refreshing]);
  if (narrow) {
    return (
      <Tooltip title={refreshing ? t("Refreshing") : text}>
        <span>
          <IconButton
            ref={buttonRef}
            aria-label={refreshing ? t("Refreshing") : text}
            onClick={handleRefresh}
            disabled={refreshing}
            aria-busy={refreshing}
          >
            {icon}
          </IconButton>
        </span>
      </Tooltip>
    );
  }
  return (
    <Button
      ref={buttonRef}
      variant="outlined"
      onClick={handleRefresh}
      disabled={refreshing}
      startIcon={icon}
      aria-busy={refreshing}
      sx={{ whiteSpace: "nowrap" }}
    >
      {refreshing ? t("Refreshing") : text}
    </Button>
  );
}

interface ListRefreshErrorProps {
  error: unknown;
  onRetry: () => unknown;
  retrying?: boolean;
}

export function ListRefreshError({ error, onRetry, retrying = false }: ListRefreshErrorProps) {
  const { t } = useTranslation();
  const retryRef = useRef<HTMLButtonElement>(null);
  const restoreRetryFocus = useRef(false);
  const retry = async () => {
    restoreRetryFocus.current = retryRef.current === document.activeElement;
    await onRetry();
    if (restoreRetryFocus.current) {
      retryRef.current?.focus();
      restoreRetryFocus.current = false;
    }
  };
  return (
    <Alert
      severity="error"
      role="alert"
      action={<Button ref={retryRef} color="inherit" size="small" disabled={retrying} onClick={() => { void retry(); }}>{retrying ? t("Refreshing") : t("Retry")}</Button>}
    >
      {t(getUserFacingErrorMessage(error) ?? "Unable to refresh results. Please try again.")}
    </Alert>
  );
}
