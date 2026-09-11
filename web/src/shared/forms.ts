import { ApiError } from '../api/client';

export type FieldErrors = Record<string, string>;

export function validEmail(email: string): boolean {
  return /^\S+@\S+\.\S+$/.test(email.trim());
}

export function applyApiError(
  error: unknown,
  setFieldErrors: (errors: FieldErrors) => void,
  setFailure: (message: string) => void,
) {
  if (error instanceof ApiError) {
    const serverErrors = Object.fromEntries(
      (error.fieldErrors ?? [])
        .filter((fieldError) => fieldError.field !== undefined && fieldError.message !== undefined)
        .map((fieldError) => [fieldError.field as string, fieldError.message as string]),
    );
    setFieldErrors(serverErrors);
    if (error.code === 'VERIFICATION_TOKEN_INVALID') {
      setFailure(
        'This verification link is invalid, expired, or has already been used. Register again to request a new link.',
      );
      return;
    }
    if (error.code === 'PASSWORD_RECOVERY_TOKEN_INVALID') {
      setFailure(
        'This recovery link is invalid, expired, or has already been used. Request a new link to reset your password.',
      );
      return;
    }
    setFailure(error.message);
    return;
  }

  setFailure('The request could not be completed. Please try again.');
}
