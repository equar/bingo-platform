import { createTheme, alpha } from "@mui/material/styles";

const ink = "#17243b";
const primary = "#2857a4";
const border = "#e1e7ef";

export const adminTheme = createTheme({
  palette: {
    mode: "light",
    primary: { main: primary, dark: "#1d4382", light: "#e8effb", contrastText: "#fff" },
    secondary: { main: "#197d75", dark: "#12655f", light: "#e6f4f1", contrastText: "#fff" },
    success: { main: "#168257", light: "#e8f5ee" },
    warning: { main: "#a66314", light: "#fff4df" },
    error: { main: "#b34242", light: "#fceded" },
    info: { main: "#286ca5", light: "#eaf3fb" },
    text: { primary: ink, secondary: "#59677c", disabled: "#8c98a9" },
    divider: border,
    background: { default: "#f4f6fa", paper: "#ffffff" },
    action: { selected: "#eaf0fa", hover: "#f4f7fb", focus: alpha(primary, 0.14) },
  },
  shape: { borderRadius: 4 },
  spacing: 8,
  typography: {
    fontFamily: [
      "Inter", "Noto Sans Ethiopic", "Ebrima", "Nyala", "Abyssinica SIL",
      "system-ui", "-apple-system", "BlinkMacSystemFont", "Segoe UI", "sans-serif",
    ].join(","),
    fontSize: 14,
    h1: { fontSize: "2rem", fontWeight: 750, letterSpacing: "-0.035em", lineHeight: 1.15 },
    h2: { fontSize: "1.75rem", fontWeight: 740, letterSpacing: "-0.03em", lineHeight: 1.2 },
    h3: { fontSize: "1.5rem", fontWeight: 720, letterSpacing: "-0.025em", lineHeight: 1.25 },
    h4: { fontSize: "1.65rem", fontWeight: 740, letterSpacing: "-0.03em", lineHeight: 1.2 },
    h5: { fontSize: "1.2rem", fontWeight: 700, letterSpacing: "-0.015em", lineHeight: 1.3 },
    h6: { fontSize: "1rem", fontWeight: 700, letterSpacing: "-0.005em", lineHeight: 1.35 },
    subtitle1: { fontSize: "0.95rem", fontWeight: 650, lineHeight: 1.45 },
    subtitle2: { fontSize: "0.82rem", fontWeight: 650, lineHeight: 1.4 },
    body1: { fontSize: "0.9rem", lineHeight: 1.55 },
    body2: { fontSize: "0.82rem", lineHeight: 1.5 },
    button: { fontSize: "0.82rem", fontWeight: 650, textTransform: "none" },
    caption: { fontSize: "0.73rem", lineHeight: 1.45 },
    overline: { fontSize: "0.68rem", fontWeight: 700, letterSpacing: "0.08em", lineHeight: 1.5 },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        html: { minWidth: 320, scrollBehavior: "smooth" },
        body: { minHeight: "100dvh", color: ink, backgroundColor: "#f4f6fa" },
        ".admin-page-content": { minWidth: 0, width: "100%" },
        ".admin-page-content > .MuiStack-root, .admin-page-content > .admin-table-page > .MuiStack-root": {
          minWidth: 0,
          gap: "16px !important",
          "@media (max-width: 600px)": { gap: "12px !important" },
        },
        ".admin-page-content .MuiGrid-root, .admin-page-content .MuiStack-root": { minWidth: 0 },
        ".admin-page-content .MuiGrid-container": { margin: "0 !important", width: "100% !important", maxWidth: "100%" },
        ".admin-page-content .MuiTableContainer-root": { width: "100%", maxWidth: "100%", minWidth: 0 },
        ".admin-page-content .MuiTableCell-body": { overflowWrap: "anywhere" },
        ".admin-table-page": {
          height: "calc(100dvh - 84px)",
          minHeight: 320,
          minWidth: 0,
          "@media (min-width: 900px)": { height: "calc(100dvh - 92px)" },
        },
        ".admin-table-page > .MuiStack-root": { height: "100%", minHeight: 0 },
        ".admin-table-page > .MuiStack-root > :has(.MuiTable-root)": { flex: "1 1 0", minHeight: 0, overflow: "auto" },
        ".admin-table-page > .MuiStack-root > .MuiPaper-root:has(.MuiTable-root)": {
          display: "flex",
          minHeight: 0,
          flexDirection: "column",
        },
        ".admin-table-page .MuiTableContainer-root": { flex: "1 1 auto", minHeight: 0 },
        "*": { boxSizing: "border-box" },
        "*:focus-visible": { outline: `3px solid ${alpha(primary, 0.65)}`, outlineOffset: 2 },
        "::selection": { color: "#173460", backgroundColor: "#cddcf4" },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: { color: ink, backgroundColor: "#fff", backgroundImage: "none", boxShadow: "0 1px 0 #e5eaf1" },
      },
    },
    MuiToolbar: { styleOverrides: { root: { minHeight: "60px !important" } } },
    MuiDrawer: {
      styleOverrides: {
        paper: { color: ink, backgroundColor: "#fff", backgroundImage: "none", borderRight: `1px solid ${border}` },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true, size: "medium" },
      styleOverrides: {
        root: { minHeight: 40, borderRadius: 10, paddingInline: 16, whiteSpace: "nowrap", transition: "background-color 120ms ease, border-color 120ms ease" },
        sizeSmall: { minHeight: 34, borderRadius: 9, paddingInline: 12 },
        containedPrimary: { boxShadow: "none", "&:hover": { boxShadow: "none" } },
        outlined: { borderColor: "#cfd8e5", "&:hover": { borderColor: "#9aabc1", backgroundColor: "#f7f9fc" } },
      },
    },
    MuiIconButton: { styleOverrides: { root: { borderRadius: 10 } } },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: { outlined: { borderColor: border, backgroundImage: "none" } },
    },
    MuiCard: {
      styleOverrides: {
        root: { border: `1px solid ${border}`, borderRadius: 14, backgroundImage: "none", boxShadow: "0 1px 2px rgba(23,36,59,0.025)" },
      },
    },
    MuiCardContent: { styleOverrides: { root: { padding: 16, "&:last-child": { paddingBottom: 16 }, "@media (max-width: 600px)": { padding: 14, "&:last-child": { paddingBottom: 14 } } } } },
    MuiTextField: { defaultProps: { size: "small", variant: "outlined" } },
    MuiOutlinedInput: {
      styleOverrides: {
        root: { borderRadius: 10, backgroundColor: "#fff", "&:hover .MuiOutlinedInput-notchedOutline": { borderColor: "#aab7c8" }, "&.Mui-focused .MuiOutlinedInput-notchedOutline": { borderWidth: 1.5 } },
        input: { paddingTop: 11, paddingBottom: 11 },
        notchedOutline: { borderColor: "#d5dde8" },
      },
    },
    MuiFormLabel: { styleOverrides: { root: { fontSize: "0.84rem" } } },
    MuiFormHelperText: { styleOverrides: { root: { marginLeft: 2, marginRight: 2, lineHeight: 1.4 } } },
    MuiTableContainer: {
      styleOverrides: { root: { border: `1px solid ${border}`, borderRadius: 13, backgroundColor: "#fff", backgroundImage: "none", overflowX: "auto" } },
    },
    MuiTable: { defaultProps: { size: "small", stickyHeader: true } },
    MuiTableCell: {
      styleOverrides: {
        root: { borderColor: "#e9edf3", padding: "8px 12px", verticalAlign: "middle" },
        head: { color: "#5d6b7f", backgroundColor: "#f7f9fc", fontSize: "0.72rem", fontWeight: 700, letterSpacing: "0.045em", textTransform: "uppercase", whiteSpace: "nowrap", borderBottom: `1px solid ${border}` },
        body: { fontSize: "0.82rem" },
      },
    },
    MuiTableRow: { styleOverrides: { root: { "&:last-child td, &:last-child th": { borderBottom: 0 }, "&:hover": { backgroundColor: "#f8faff" } } } },
    MuiTablePagination: {
      styleOverrides: {
        root: { color: "#59677c", borderTop: `1px solid ${border}`, minHeight: 48 },
        toolbar: { minHeight: "48px !important", paddingInline: 8 },
        selectLabel: { fontSize: "0.76rem" },
        displayedRows: { fontSize: "0.76rem" },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { minHeight: 25, borderRadius: 7, fontWeight: 650, backgroundColor: "#f0f3f7" },
        sizeSmall: { minHeight: 23 },
        label: { paddingInline: 9, fontSize: "0.74rem" },
        colorPrimary: { color: "#244e8e", backgroundColor: "#e9effa" },
        colorSuccess: { color: "#176543", backgroundColor: "#e8f5ee" },
        colorWarning: { color: "#86500e", backgroundColor: "#fff2dc" },
        colorError: { color: "#963737", backgroundColor: "#fbeaea" },
        colorInfo: { color: "#265f8e", backgroundColor: "#eaf3fb" },
      },
    },
    MuiAlert: {
      styleOverrides: { root: { borderRadius: 11, alignItems: "center" }, message: { minWidth: 0 } },
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          maxHeight: "calc(100dvh - 32px)",
          borderRadius: 16,
          border: `1px solid ${border}`,
          boxShadow: "0 24px 70px rgba(23,36,59,0.18)",
          "@media (max-width: 600px)": { margin: 8, maxHeight: "calc(100dvh - 16px)", width: "calc(100% - 16px)" },
        },
      },
    },
    MuiDialogContent: { styleOverrides: { root: { minHeight: 0, overflowY: "auto" } } },
    MuiDialogActions: { styleOverrides: { root: { flexWrap: "wrap", gap: 8 } } },
    MuiTooltip: { styleOverrides: { tooltip: { borderRadius: 7, fontSize: "0.74rem", backgroundColor: "#24334c" } } },
    MuiLink: { styleOverrides: { root: { textUnderlineOffset: "3px", textDecorationThickness: "1px" } } },
  },
});
