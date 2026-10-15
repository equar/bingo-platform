import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
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
  TablePagination,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { adminUsers, createAdminUser, updateAdminUser, type UserSummaryResponse } from "../../api/admin";
import { StatusState } from "../../components/StatusState";

const roleOptions = ["ADMIN", "SUPPORT"] as const;
const statusOptions = ["ACTIVE", "PENDING", "SUSPENDED", "DISABLED"] as const;

interface AdminUserFormState {
  displayName: string;
  email: string;
  phoneNumber: string;
  password?: string;
  status: string;
  role: string;
}

const emptyForm: AdminUserFormState = {
  displayName: "",
  email: "",
  phoneNumber: "",
  status: "ACTIVE",
  role: "ADMIN",
};

function roleLabel(role: string) {
  return role;
}

export function AdminUsersPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserSummaryResponse | null>(null);
  const [form, setForm] = useState<AdminUserFormState>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);

  const usersQuery = useQuery({
    queryKey: ["admin-users", page, pageSize, search, statusFilter],
    queryFn: () => adminUsers(page, pageSize, search, statusFilter),
  });

  const users = usersQuery.data?.content ?? [];

  const openCreate = () => {
    setSelectedUser(null);
    setForm(emptyForm);
    setFormError(null);
    setDialogOpen(true);
  };

  const openEdit = (user: UserSummaryResponse) => {
    setSelectedUser(user);
    setForm({
      displayName: user.displayName,
      email: user.email,
      phoneNumber: user.phoneNumber,
      status: user.status,
      role: user.roles.find((role) => roleOptions.includes(role as (typeof roleOptions)[number])) ?? "ADMIN",
    });
    setFormError(null);
    setDialogOpen(true);
  };

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (selectedUser) {
        return updateAdminUser(selectedUser.id, {
          displayName: form.displayName,
          email: form.email,
          phoneNumber: form.phoneNumber,
          password: form.password?.trim() ? form.password : undefined,
          status: form.status,
          roles: [form.role],
        });
      }

      return createAdminUser({
        displayName: form.displayName,
        email: form.email,
        phoneNumber: form.phoneNumber,
        status: form.status,
        roles: [form.role],
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin-users"] });
      setDialogOpen(false);
      setSelectedUser(null);
      setForm(emptyForm);
      setFormError(null);
    },
    onError: (error) => setFormError((error as Error).message),
  });

  if (usersQuery.isLoading) {
    return <StatusState title="Loading admin users" loading message="Fetching admin and support accounts" />;
  }

  if (usersQuery.error) {
    return (
      <StatusState
        title="Unable to load admin users"
        message={(usersQuery.error as Error).message}
        actionLabel="Retry"
        onAction={() => usersQuery.refetch()}
      />
    );
  }

  return (
    <Stack spacing={3}>
      <Box display="flex" justifyContent="space-between" alignItems="center" gap={2} flexWrap="wrap">
        <div>
          <Typography variant="h4">Admin Users</Typography>
          <Typography color="text.secondary">
            Create and maintain admin and support accounts for the console.
          </Typography>
        </div>
        <Button variant="contained" onClick={openCreate}>
          Create admin user
        </Button>
      </Box>

      <Alert severity="info">This page manages ADMIN and SUPPORT accounts only.</Alert>

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <TextField
          label="Search by name, email, or phone"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
          fullWidth
        />
        <TextField
          select
          label="Status"
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {statusOptions.map((option) => (
            <MenuItem key={option} value={option}>
              {option}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {users.length === 0 ? <Alert severity="info">No admin users match the current filters.</Alert> : null}

      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label="admin users table">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Phone</TableCell>
              <TableCell>Roles</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Updated</TableCell>
              <TableCell align="right">Actions</TableCell>
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
                      <Chip key={role} label={roleLabel(role)} size="small" />
                    ))}
                  </Stack>
                </TableCell>
                <TableCell>{user.status}</TableCell>
                <TableCell>{new Date(user.updatedAt).toLocaleString()}</TableCell>
                <TableCell align="right">
                  <Button size="small" onClick={() => openEdit(user)}>
                    Edit
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>

      <TablePagination
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
        <DialogTitle>{selectedUser ? "Edit admin user" : "Create admin user"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="Display name"
              value={form.displayName}
              onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))}
              fullWidth
              required
            />
            <TextField
              label="Email"
              value={form.email}
              onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
              fullWidth
              required
            />
            <TextField
              label="Phone number"
              value={form.phoneNumber}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  phoneNumber: event.target.value.replace(/\D/g, "").slice(0, 10),
                }))
              }
              fullWidth
              required
              inputProps={{ inputMode: "numeric", maxLength: 10 }}
              helperText="Enter exactly 10 digits."
            />
            {!selectedUser ? (
              <Alert severity="info">A password will be generated automatically and emailed to the new user.</Alert>
            ) : null}
            {selectedUser ? (
              <TextField
                label="Password (leave blank to keep current)"
                type="password"
                value={form.password ?? ""}
                onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                fullWidth
                helperText="Leave blank to keep the current password."
              />
            ) : null}
            <TextField
              select
              label="Status"
              value={form.status}
              onChange={(event) => setForm((current) => ({ ...current, status: event.target.value }))}
              fullWidth
            >
              {statusOptions.map((option) => (
                <MenuItem key={option} value={option}>
                  {option}
                </MenuItem>
              ))}
            </TextField>
            <FormControl fullWidth>
              <InputLabel id="admin-user-role-label">Role</InputLabel>
              <Select
                labelId="admin-user-role-label"
                label="Role"
                value={form.role}
                onChange={(event) => setForm((current) => ({ ...current, role: event.target.value }))}
              >
                {roleOptions.map((role) => (
                  <MenuItem key={role} value={role}>
                    {role}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Typography variant="body2" color="text.secondary">
              Choose either ADMIN or SUPPORT.
            </Typography>
            {formError ? <Alert severity="error">{formError}</Alert> : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={saveMutation.isPending}>
            Cancel
          </Button>
          <Button variant="contained" onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
            {saveMutation.isPending ? "Saving..." : selectedUser ? "Save changes" : "Create user"}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
