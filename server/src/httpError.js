export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export function notFound(message = 'Not found') {
  return new HttpError(404, message);
}

export function badRequest(message) {
  return new HttpError(400, message);
}

export function unauthorized(message = 'Missing or invalid device identity') {
  return new HttpError(401, message);
}

export function forbidden(message = 'Forbidden') {
  return new HttpError(403, message);
}
