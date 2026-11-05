import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { adminUsers, createAdminUser, resendAdminUserActivation, resetAdminUserPassword, revokeFinanceRole, updateAdminUser, type UserSummaryResponse } from "../../api/admin";
import { getUserFacingErrorMessage, parseApiError } from "../../api/types";
import { StatusState } from "../../components/StatusState";
import { AdminTablePagination } from "../../components/AdminTablePagination";
import { translateAdminRole, translateAdminStatus } from "../../i18n";
import { useSession } from "../../api/session";

const roleOptions = ["ADMIN", "SUPPORT"] as const;
const statusOptions = ["ACTIVE", "PENDING", "SUSPENDED", "DISABLED"] as const;

interface AdminUserFormState {
  displayName: string;
  email: string;
  phoneNumber: string;
  status: string;
  role: string;
}

const emptyForm: AdminUserFormState = {
  displayName: "",
  email: "",
  phoneNumber: "",
  status: "PENDING",
  role: "ADMIN",
};

function toLocalEthiopianPhone(phoneNumber: string | null) {
  if (!phoneNumber) return "";
  const normalized = phoneNumber.replace(/\D/g, "");
  return normalized.startsWith("251") && normalized.length === 12
    ? `0${normalized.slice(3)}`
    : phoneNumber;
}

function toAdminUserForm(user: UserSummaryResponse): AdminUserFormState {
  return {
    displayName: user.displayName,
    email: user.email,
    phoneNumber: toLocalEthiopianPhone(user.phoneNumber),
    status: user.status,
    role: user.roles.find((role) => roleOptions.includes(role as (typeof roleOptions)[number])) ?? "ADMIN",
  };
}

function hasAdminUserFormChanges(form: AdminUserFormState, user: UserSummaryResponse) {
  const original = toAdminUserForm(user);
  return form.displayName !== original.displayName
    || form.email !== original.email
    || form.phoneNumber !== original.phoneNumber
    || form.status !== original.status
    || form.role !== original.role;
}

export function AdminUsersPage() {
  const { t } = useTranslation();
  const session = useSession();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [resendError, setResendError] = useState<string | null>(null);
  const [resendSuccess, setResendSuccess] = useState<string | null>(null);
  const [selectedUser, setSelectedUser] = useState<UserSummaryResponse | null>(null);
  const [resetTarget, setResetTarget] = useState<UserSummaryResponse | null>(null);
  const [form, setForm] = useState<AdminUserFormState>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [phoneError, setPhoneError] = useState<string | null>(null);
  const [roleError, setRoleError] = useState<string | null>(null);

  const revokeFinanceMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: number; role: string }) => revokeFinanceRole(userId, role),
    onSuccess: async () => {
      setRoleError(null);
      await queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (error) => setRoleError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
  });

  const usersQuery = useQuery({
    queryKey: ["admin-users", page, pageSize, search, statusFilter],
    queryFn: () => adminUsers(page, pageSize, search, statusFilter),
  });

  const resendMutation = useMutation({
    mutationFn: (userId: number) => resendAdminUserActivation(userId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin-users"] });
      setResendError(null);
      setResendSuccess("Activation email queued.");
    },
    onError: (error) => setResendError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again."),
  });

  const resetPasswordMutation = useMutation({
    mutationFn: (userId: number) => resetAdminUserPassword(userId),
    onSuccess: () => {
      setResetTarget(null);
      setResendSuccess("Password reset email queued. The user must reset their password before signing in.");
    },
    onError: (error) => {
      setResetTarget(null);
      setResendError(getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again.");
    },
  });

  const users = usersQuery.data?.content ?? [];
  const isEditingOwnAccount = selectedUser?.id === session?.user.id;

  const validateForm = () => {
    if (!form.displayName.trim()) return "Enter a display name.";
    if (!selectedUser && (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()) || form.email !== form.email.trim())) {
      return "Enter a valid email address.";
    }
    if (form.phoneNumber && !/^\d{10}$/.test(form.phoneNumber)) return "Enter exactly 10 digits.";
    if (!statusOptions.some((status) => status === form.status)) return "Choose a valid status.";
    if (!selectedUser && !roleOptions.some((role) => role === form.role)) return "Choose a valid role.";
    return null;
  };

  const submitForm = () => {
    const validationError = validateForm();
    if (validationError) {
      setFormError(validationError);
      return;
    }
    setFormError(null);
    setEmailError(null);
    setPhoneError(null);
    saveMutation.mutate();
  };

  const openCreate = () => {
    setSelectedUser(null);
    setForm(emptyForm);
    setFormError(null);
    setEmailError(null);
    setPhoneError(null);
    setDialogOpen(true);
  };

  const openEdit = (user: UserSummaryResponse) => {
    setSelectedUser(user);
    setForm(toAdminUserForm(user));
    setFormError(null);
    setEmailError(null);
    setPhoneError(null);
    setDialogOpen(true);
  };

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (selectedUser) {
        return updateAdminUser(selectedUser.id, {
          displayName: form.displayName,
          email: form.email,
          phoneNumber: form.phoneNumber || null,
          status: form.status,
          roles: selectedUser.roles,
        });
      }

      return createAdminUser({
        displayName: form.displayName,
        email: form.email,
        phoneNumber: form.phoneNumber || null,
        status: "PENDING",
        roles: [form.role],
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin-users"] });
      setDialogOpen(false);
      setSelectedUser(null);
      setForm(emptyForm);
      setFormError(null);
      setEmailError(null);
      setPhoneError(null);
    },
    onError: (error) => {
      const apiError = parseApiError(error);
      const message = getUserFacingErrorMessage(error) ?? "Something went wrong. Please try again.";
      if (apiError.code === "EMAIL_ALREADY_REGISTERED") {
        setEmailError(message);
        setFormError(null);
        return;
      }
      if (apiError.code === "PHONE_ALREADY_REGISTERED") {
        setPhoneError(message);
        setFormError(null);
        return;
      }
      setFormError(message);
    },
  });
  const hasEditChanges = selectedUser !== null && hasAdminUserFormChanges(form, selectedUser);

  if (usersQuery.isLoading) {
    return <StatusState title={t("Loading admin users")} loading message={t("Fetching admin and support accounts")} />;
  }

  if (usersQuery.error) {
    return (
      <StatusState
        title={t("Unable to load admin users")}
        message={t(getUserFacingErrorMessage(usersQuery.error) ?? "Something went wrong. Please try again.")}
        actionLabel={t("Retry")}
        onAction={() => usersQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Typography variant="h4">{t("Admin Users")}</Typography>
          <Typography color="text.secondary">
            {t("Create and maintain admin and support accounts for the console.")}</Typography>
        </div>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
          <Button variant="contained" onClick={openCreate}>{t("Create admin user")}</Button>
        </Stack>
      </Box>

      <Alert severity="info">{t("This page manages ADMIN and SUPPORT accounts only.")}</Alert>
      {roleError ? <Alert severity="error" role="alert">{t(roleError)}</Alert> : null}
      {resendError && !resetTarget && !dialogOpen ? <Alert severity="error" role="alert">{t(resendError)}</Alert> : null}
      {resendSuccess ? <Alert severity="success" role="status">{t(resendSuccess)}</Alert> : null}

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label={t("Search by name, email, or phone")}
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
          fullWidth
        />
        <TextField
          select
          label={t("Status")}
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value="">{t("All statuses")}</MenuItem>
          {statusOptions.map((option) => (
            <MenuItem key={option} value={option}>
              {translateAdminStatus(option, t)}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {users.length === 0 ? <Alert severity="info">{t("No admin users match the current filters.")}</Alert> : null}

      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label={t("admin users table")}>
          <TableHead>
            <TableRow>
              <TableCell>{t("Name")}</TableCell>
              <TableCell>{t("Email")}</TableCell>
              <TableCell>{t("Phone")}</TableCell>
              <TableCell>{t("Roles")}</TableCell>
              <TableCell>{t("Status")}</TableCell>
              <TableCell>{t("Updated")}</TableCell>
              <TableCell align="right">{t("Actions")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id} hover>
                <TableCell>
                  <Typography fontWeight={600}>{user.displayName}</Typography>
                </TableCell>
                <TableCell>{user.email}</TableCell>
                <TableCell>{user.phoneNumber}</TableCell>
                <TableCell>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {user.roles.map((role) => (
                      <Chip key={role} label={translateAdminRole(role, t)} size="small" />
                    ))}
                  </Stack>
                </TableCell>
                <TableCell>{translateAdminStatus(user.status, t)}</TableCell>
                <TableCell>{new Date(user.updatedAt).toLocaleString()}</TableCell>
                <TableCell align="right">
                  <Button size="small" onClick={() => openEdit(user)}>{t("Edit")}</Button>
                  {user.roles.filter((role) => role === "FINANCE_VERIFIER" || role === "FINANCE_APPROVER").map((role) => (
                    <Button
                      key={role}
                      size="small"
                      color="warning"
                      aria-label={t("Revoke {{role}} from {{name}}", {
                        role: translateAdminRole(role, t),
                        name: user.displayName,
                      })}
                      disabled={revokeFinanceMutation.isPending}
                      onClick={() => revokeFinanceMutation.mutate({ userId: user.id, role })}
                    >
                      {t("Revoke")}{" "}{translateAdminRole(role, t)}
                    </Button>
                  ))}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>

      <AdminTablePagination
        component="div"
        count={usersQuery.data?.totalElements ?? 0}
        page={page}
        onPageChange={(_, nextPage) => setPage(nextPage)}
        rowsPerPage={pageSize}
        onRowsPerPageChange={(event) => {
          setPageSize(Number(event.target.value));
          setPage(0);
        }}
        rowsPerPageOptions={[10, 20, 50]}
      />

      <Dialog
        open={dialogOpen}
        onClose={() => (saveMutation.isPending ? undefined : setDialogOpen(false))}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>
          <Box display="flex" alignItems="center" justifyContent="space-between" gap={2} flexWrap="wrap">
            <span>{selectedUser ? t("Edit admin user") : t("Create admin user")}</span>
            {selectedUser?.status === "PENDING" ? (
              <Button
                size="small"
                disabled={isEditingOwnAccount || resendMutation.isPending}
                title={isEditingOwnAccount ? t("You cannot resend an activation email to your own account.") : undefined}
                onClick={() => {
                  setResendError(null);
                  setResendSuccess(null);
                  resendMutation.mutate(selectedUser.id);
                }}
              >{resendMutation.isPending ? t("Sending...") : t("Resend activation email")}</Button>
            ) : selectedUser?.status === "ACTIVE" ? (
              <Button
                size="small"
                color="warning"
                disabled={isEditingOwnAccount || resetPasswordMutation.isPending}
                title={isEditingOwnAccount ? t("You cannot send a password reset link to your own account.") : undefined}
                onClick={() => {
                  setResendError(null);
                  setResendSuccess(null);
                  setResetTarget(selectedUser);
                }}
              >{t("Reset password")}</Button>
            ) : null}
          </Box>
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {resendSuccess ? <Alert severity="success" role="status">{t(resendSuccess)}</Alert> : null}
            {resendError && selectedUser && !resetTarget ? <Alert severity="error" role="alert">{t(resendError)}</Alert> : null}
            <TextField
              label={t("Display name")}
              value={form.displayName}
              onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))}
              fullWidth
              required
            />
            <TextField
              label={t("Email")}
              type="email"
              value={form.email}
              onChange={(event) => {
                setEmailError(null);
                setForm((current) => ({ ...current, email: event.target.value }));
              }}
              fullWidth
              required
              disabled={Boolean(selectedUser)}
              error={Boolean(emailError)}
              helperText={emailError ? t(emailError) : selectedUser ? t("Email cannot be changed after account creation.") : undefined}
            />
            <TextField
              label={t("Phone number (optional)")}
              value={form.phoneNumber}
              onChange={(event) => {
                setPhoneError(null);
                setForm((current) => ({
                  ...current,
                  phoneNumber: event.target.value.replace(/\D/g, "").slice(0, 10),
                }));
              }}
              fullWidth
              error={Boolean(phoneError)}
              inputProps={{ inputMode: "numeric", maxLength: 10 }}
              helperText={phoneError ? t(phoneError) : t("Leave blank or enter exactly 10 digits.")}
            />
            {!selectedUser ? (
              <Alert severity="info">{t("A password will be generated automatically and emailed to the new user.")}</Alert>
            ) : null}
            {selectedUser ? (
              <TextField
                select
                label={t("Status")}
                value={form.status}
                onChange={(event) => setForm((current) => ({ ...current, status: event.target.value }))}
                fullWidth
                disabled={isEditingOwnAccount || selectedUser.status === "PENDING" || selectedUser.status === "DISABLED"}
                helperText={isEditingOwnAccount
                  ? t("You cannot change your own account status.")
                  : selectedUser.status === "PENDING"
                  ? t("Status stays pending until the invited user activates the account.")
                  : selectedUser.status === "DISABLED"
                    ? t("Disabled accounts are permanently deactivated and cannot be reactivated.")
                    : form.status === "DISABLED"
                      ? t("Disabling an account is permanent and cannot be undone.")
                      : form.status === "SUSPENDED"
                        ? t("Suspension temporarily blocks sign-in and can be reversed later.")
                        : undefined}
              >
                {(selectedUser.status === "PENDING"
                  ? ["PENDING"]
                  : selectedUser.status === "DISABLED"
                    ? ["DISABLED"]
                    : statusOptions.filter((option) => option !== "PENDING")
                ).map((option) => (
                  <MenuItem key={option} value={option}>
                    {translateAdminStatus(option, t)}
                  </MenuItem>
                ))}
              </TextField>
            ) : null}
            {selectedUser ? (
              <TextField
                label={t("Role")}
                value={selectedUser.roles.map((role) => translateAdminRole(role, t)).join(", ")}
                fullWidth
                disabled
                helperText={t("Role cannot be changed after account creation.")}
              />
            ) : (
              <>
                <FormControl fullWidth>
                  <InputLabel id="admin-user-role-label">{t("Role")}</InputLabel>
                  <Select
                    labelId="admin-user-role-label"
                    label={t("Role")}
                    value={form.role}
                    onChange={(event) => setForm((current) => ({ ...current, role: event.target.value }))}
                  >
                    {roleOptions.map((role) => (
                      <MenuItem key={role} value={role}>
                        {translateAdminRole(role, t)}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <Typography variant="body2" color="text.secondary">
                  {t("Choose either ADMIN or SUPPORT.")}</Typography>
              </>
            )}
            {formError ? <Alert severity="error">{t(formError)}</Alert> : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={saveMutation.isPending}>
            {t("Cancel")}</Button>
          <Button
            variant="contained"
            onClick={submitForm}
            disabled={saveMutation.isPending || (selectedUser !== null && !hasEditChanges)}
          >
            {saveMutation.isPending ? t("Saving...") : selectedUser ? t("Save changes") : t("Create user")}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(resetTarget)} onClose={() => (resetPasswordMutation.isPending ? undefined : setResetTarget(null))} fullWidth maxWidth="xs">
        <DialogTitle>{t("Send password reset link?")}</DialogTitle>
        <DialogContent>
          <Typography color="text.secondary">
            {t("A reset link will be sent to {{email}}. They cannot sign in until they choose a new password.", { email: resetTarget?.email ?? "" })}
          </Typography>
          {resendError ? <Alert severity="error" role="alert" sx={{ mt: 2 }}>{t(resendError)}</Alert> : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResetTarget(null)} disabled={resetPasswordMutation.isPending}>{t("Cancel")}</Button>
          <Button
            variant="contained"
            color="warning"
            disabled={!resetTarget || resetPasswordMutation.isPending}
            onClick={() => resetTarget && resetPasswordMutation.mutate(resetTarget.id)}
          >{resetPasswordMutation.isPending ? t("Sending...") : t("Send reset link")}</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
