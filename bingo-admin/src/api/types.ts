export interface ValidationErrorDetail {
  field: string;
  message: string;
}

export interface ApiErrorResponse {
  timestamp?: string;
  status: number;
  error?: string;
  code?: string;
  message?: string;
  path?: string;
  correlationId?: string;
  details?: ValidationErrorDetail[];
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
    public readonly details: ValidationErrorDetail[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError;
}

export function parseApiError(error: unknown): ApiError {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { status?: number; data?: ApiErrorResponse } }).response;
    const data = response?.data;
    if (data && typeof data === 'object') {
      return new ApiError(
        data.message ?? data.error ?? 'Request failed',
        data.status ?? response?.status ?? 0,
        data.code,
        data.details ?? [],
      );
    }
    return new ApiError('Request failed', response?.status ?? 0);
  }

  if (error instanceof Error) {
    return new ApiError(error.message, 0);
  }

  return new ApiError('Request failed', 0);
}

export function pageDefaults<T>(): PageResponse<T> {
  return {
    content: [],
    page: 0,
    size: 0,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  };
}
