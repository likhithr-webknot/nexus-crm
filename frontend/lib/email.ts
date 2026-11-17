/* Email checks that match the backend (shared/Emails.java), which has the last word: one plain address, nothing else. */

const ADDRESS =
  /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])$/;

export const MAX_COPIES = 20;

export function isValidEmail(email: string) {
  return email.length <= 254 && email.indexOf("@") <= 64 && ADDRESS.test(email);
}

/** Splits "a@x.com, b@y.com; c@z.com" into lower-case addresses without repeats. */
export function splitAddresses(text: string) {
  return [
    ...new Set(
      text
        .split(/[\s,;]+/)
        .map((part) => part.trim().toLowerCase())
        .filter(Boolean),
    ),
  ];
}
