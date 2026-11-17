/* Email addresses typed into a form. The rules match the backend (shared/Emails.java), which has the last word:
   one plain address, with no spaces, commas, semicolons, angle brackets or quotes. */

const ADDRESS =
  /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])$/;

export const MAX_COPIES = 20;

export function isValidEmail(email: string) {
  return email.length <= 254 && email.indexOf("@") <= 64 && ADDRESS.test(email);
}

/** Splits what someone typed or pasted ("a@x.com, b@y.com; c@z.com") into separate lower-case addresses, without repeats. */
export function splitAddresses(text: string) {
  return [...new Set(text.split(/[\s,;]+/).map((part) => part.trim().toLowerCase()).filter(Boolean))];
}
