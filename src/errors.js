/** Base application error carrying an HTTP status and a stable machine-readable code. */
export class AppError extends Error {
  constructor(message, status = 500, code = 'INTERNAL_ERROR') {
    super(message);
    this.name = this.constructor.name;
    this.status = status;
    this.code = code;
  }
}

export class ValidationError extends AppError {
  constructor(message) { super(message, 400, 'VALIDATION_ERROR'); }
}

export class AuthenticationError extends AppError {
  constructor(message = 'Please sign in to continue.') { super(message, 401, 'UNAUTHENTICATED'); }
}

export class ForbiddenError extends AppError {
  constructor(message = 'You do not have permission to perform this action.') { super(message, 403, 'FORBIDDEN'); }
}

export class NotFoundError extends AppError {
  constructor(message = 'Not found.') { super(message, 404, 'NOT_FOUND'); }
}

export class ConflictError extends AppError {
  constructor(message) { super(message, 409, 'CONFLICT'); }
}

export class ServiceUnavailableError extends AppError {
  constructor(message) { super(message, 503, 'SERVICE_UNAVAILABLE'); }
}
