import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Card,
  CardContent,
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
import { usePrivateListSearch } from "../../components/usePrivateListSearch";
import { useListSessionState } from "../../components/useListSessionState";
import { AdminTablePage } from "../../components/AdminTablePage";
import { AdminPageHeader } from "../../components/AdminPageHeader";
import { translateAdminRole, translateAdminStatus } from "../../i18n";
import { useSession } from "../../api/session";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { ConfirmDialog } from "../../components/ConfirmDialog";

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
  const location = useLocation();
  const navigate = useNavigate();
  const { userId: userIdParam } = useParams<{ userId: string }>();
  const isCreatePage = location.pathname === "/admin-users/new";
  const isEditPage = location.pathname.endsWith("/edit");
  const isEditorPage = isCreatePage || isEditPage;
  const session = useSession();
  const queryClient = useQueryClient();
  const [page, setPage] = useListSessionState("admin-users:page", 0);
  const [pageSize, setPageSize] = useListSessionState("admin-users:size", 20);
  const [search, setSearch] = usePrivateListSearch("admin-users");
  const [statusFilter, setStatusFilter] = useListSessionState("admin-users:status", "");
  const [discardOpen, setDiscardOpen] = useState(false);
  const [resendError, setResendError] = useState<string | null>(null);
  const [resendSuccess, setResendSuccess] = useState<string | null>(null);
  const routeUser = (location.state as { adminUser?: UserSummaryResponse } | null)?.adminUser;
  const routeUserId = Number(userIdParam);
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

  const selectedUser = isEditPage
    ? (routeUser?.id === routeUserId ? routeUser : usersQuery.data?.content.find((user) => user.id === routeUserId) ?? null)
    : null;

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

  useEffect(() => {
    if (!isEditorPage) return;
    if (isCreatePage) {
      setForm(emptyForm);
      setFormError(null);
      setEmailError(null);
      setPhoneError(null);
      setResendError(null);
      setResendSuccess(null);
    } else if (selectedUser) {
      setForm(toAdminUserForm(selectedUser));
      setFormError(null);
      setEmailError(null);
      setPhoneError(null);
      setResendError(null);
      setResendSuccess(null);
    }
  }, [location.pathname, isEditorPage, isCreatePage, selectedUser]);

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
      navigate(`/admin-users${location.search}`);
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
  const hasUnsavedChanges = selectedUser
    ? hasEditChanges
    : Boolean(form.displayName || form.email || form.phoneNumber || form.role !== emptyForm.role || form.status !== emptyForm.status);

  const cancelEditor = () => {
    if (hasUnsavedChanges) setDiscardOpen(true);
    else navigate(`/admin-users${location.search}`);
  };

  const openCreate = () => navigate(`/admin-users/new${location.search}`);
  const openEdit = (user: UserSummaryResponse) => navigate(`/admin-users/${user.id}/edit${location.search}`, { state: { adminUser: user } });

  if (!isEditorPage && usersQuery.isLoading) {
    return <StatusState title={t("Loading admin users")} loading message={t("Fetching admin and support accounts")} />;
  }

  if (!isEditorPage && usersQuery.error) {
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
    <AdminTablePage>
    <Stack spacing={3}>
      {!isEditorPage ? <>
      <AdminPageHeader title={t("Admin users")} description={t("Create and maintain admin and support accounts for the console.")} actions={
        <Button variant="contained" onClick={openCreate}>{t("Create admin user")}</Button>
      } />

      <Alert severity="info">{t("This page manages ADMIN and SUPPORT accounts only.")}</Alert>
      {roleError ? <Alert severity="error" role="alert">{t(roleError)}</Alert> : null}
      {resendError && !resetTarget ? <Alert severity="error" role="alert">{t(resendError)}</Alert> : null}
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
      </> : null}

      {isEditorPage ? <>
        <AdminPageHeader
          title={t(selectedUser ? "Edit admin user" : "Create admin user")}
          description={t(selectedUser ? "Update this admin or support account." : "Create an admin or support account.")}
          actions={<Button onClick={cancelEditor}>{t("Cancel")}</Button>}
        />
        {isEditPage && usersQuery.isLoading ? <StatusState title={t("Loading admin user")} loading message={t("Fetching admin and support accounts")} /> : null}
        {isEditPage && !usersQuery.isLoading && !selectedUser ? (
          <Alert severity="error" action={<Button color="inherit" onClick={() => navigate(`/admin-users${location.search}`)}>{t("Back to admin users")}</Button>}>
            {t("Admin user not found. Return to the list and open the account again.")}
          </Alert>
        ) : null}
        {(!isEditPage || selectedUser) && !(isEditPage && usersQuery.isLoading) ? <Card component="section" aria-label={t(selectedUser ? "Edit admin user" : "Create admin user")}>
          <CardContent sx={{
            display: "grid",
            gridTemplateColumns: selectedUser
              ? { xs: "minmax(0, 1fr)", lg: "minmax(0, 2fr) minmax(260px, 1fr)" }
              : "minmax(0, 1fr)",
            gap: 3,
          }}>
          <Stack spacing={2} minWidth={0}>
          {selectedUser?.status === "PENDING" || selectedUser?.status === "ACTIVE" ? <Box display="flex" justifyContent="flex-end" gap={1} flexWrap="wrap">
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
          </Box> : null}
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
          {selectedUser ? (
            <Box
              component="section"
              aria-label={t("Account activity")}
              sx={{
                borderLeft: { lg: 1 },
                borderTop: { xs: 1, lg: 0 },
                borderColor: "divider",
                pl: { lg: 2 },
                pt: { xs: 2, lg: 0 },
                minWidth: 0,
              }}
            >
              <Typography variant="subtitle1" fontWeight={600} gutterBottom>
                {t("Account activity")}
              </Typography>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="caption" color="text.secondary">{t("Created by")}</Typography>
                  <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
                    {selectedUser.createdByDisplayName || t("Not recorded")}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">{t("Created at")}</Typography>
                  <Typography variant="body2">{new Date(selectedUser.createdAt).toLocaleString()}</Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">{t("Updated by")}</Typography>
                  <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
                    {selectedUser.updatedByDisplayName || t("Not recorded")}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">{t("Updated at")}</Typography>
                  <Typography variant="body2">{new Date(selectedUser.updatedAt).toLocaleString()}</Typography>
                </Box>
              </Stack>
            </Box>
          ) : null}
          </CardContent>
          <Stack direction="row" justifyContent="flex-end" spacing={1} sx={{ p: 2, pt: 0 }}>
          <Button onClick={cancelEditor} disabled={saveMutation.isPending}>{t("Cancel")}</Button>
          <Button
            variant="contained"
            onClick={submitForm}
            disabled={saveMutation.isPending || (selectedUser !== null && !hasEditChanges)}
          >
            {saveMutation.isPending ? t("Saving...") : selectedUser ? t("Save changes") : t("Create admin user")}
          </Button>
          </Stack>
        </Card> : null}
      </> : null}

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

      <ConfirmDialog
        open={discardOpen}
        title={t("Discard unsaved changes?")}
        description={t("Your changes have not been saved. Discard them?")}
        confirmLabel={t("Discard")}
        cancelLabel={t("Keep editing")}
        onCancel={() => setDiscardOpen(false)}
        onConfirm={() => {
          setDiscardOpen(false);
          navigate(`/admin-users${location.search}`);
        }}
      />
    </Stack>
    </AdminTablePage>
  );
}
