import { Alert, Dialog, DialogActions, DialogContent, DialogTitle, Button, Stack, Typography } from "@mui/material";

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel: string;
  confirming?: boolean;
  error?: string;
  onCancel: () => void;
  onConfirm: () => void;
}

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel,
  confirming,
  error,
  onCancel,
  onConfirm,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onClose={confirming ? undefined : onCancel} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography>{description}</Typography>
          {error ? <Alert severity="error">{error}</Alert> : null}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={confirming}>
          {cancelLabel}
        </Button>
        <Button variant="contained" color="warning" onClick={onConfirm} disabled={confirming}>
          {confirming ? "..." : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
