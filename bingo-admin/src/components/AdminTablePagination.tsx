import { TablePagination, type TablePaginationProps } from "@mui/material";
import { useTranslation } from "react-i18next";
import { useEffect } from "react";

const pageButtonLabels = {
  first: "Go to first page",
  last: "Go to last page",
  next: "Go to next page",
  previous: "Go to previous page",
} as const;

type AdminTablePaginationProps = TablePaginationProps & { dataLoaded?: boolean };

export function AdminTablePagination(props: AdminTablePaginationProps) {
  const { t } = useTranslation();
  const { dataLoaded = false, page, count, rowsPerPage, onPageChange, ...tableProps } = props;
  const paginationProps = tableProps as TablePaginationProps;
  const lastPage = rowsPerPage > 0 && count >= 0
    ? Math.max(0, Math.ceil(count / rowsPerPage) - 1)
    : page;
  useEffect(() => {
    if (dataLoaded && page > lastPage) onPageChange?.(null, lastPage);
  }, [dataLoaded, lastPage, onPageChange, page]);
  const sx = Array.isArray(props.sx) ? props.sx : props.sx ? [props.sx] : [];

  return (
    <TablePagination
      {...paginationProps}
      count={count}
      page={dataLoaded ? page : Math.min(page, lastPage)}
      onPageChange={onPageChange}
      rowsPerPage={rowsPerPage}
      aria-label={t("Pagination")}
      getItemAriaLabel={(type) => t(pageButtonLabels[type])}
      labelRowsPerPage={t("Rows per page:")}
      labelDisplayedRows={({ from, to, count }) =>
        count === -1
          ? t("{{from}}–{{to}} of more than {{to}}", { from, to })
          : t("{{from}}–{{to}} of {{count}}", { from, to, count })
      }
      sx={[
        {
          minWidth: 0,
          width: "100%",
          maxWidth: "100%",
          overflowX: "visible",
          "& .MuiTablePagination-toolbar": {
            boxSizing: "border-box",
            minWidth: 0,
            width: "100%",
            px: { xs: 0, sm: 2 },
            flexWrap: { xs: "wrap", sm: "nowrap" },
          },
          "& .MuiTablePagination-spacer": {
            display: { xs: "none", sm: "block" },
          },
          "& .MuiTablePagination-selectLabel": {
            whiteSpace: "nowrap",
          },
          "& .MuiTablePagination-select": {
            flexShrink: 0,
          },
          "& .MuiTablePagination-displayedRows": {
            flexShrink: 0,
            ml: { xs: "auto", sm: 2 },
            whiteSpace: "nowrap",
          },
          "& .MuiTablePagination-actions": {
            flexShrink: 0,
            ml: { xs: 0, sm: 2 },
          },
        },
        ...sx,
      ]}
    />
  );
}
