import { currencyMinorUnits, decimalPlaces } from "@/lib/currency";

/**
 * Checks an amount against its currency's decimals (INR has 2, KWD has 3). When there are too many, the form
 * field is marked invalid with a message and this returns true, so the caller can stop.
 */
export function tooManyDecimals(form: HTMLFormElement, field: string, value: string, currency: string) {
  if (!value || decimalPlaces(value) <= currencyMinorUnits(currency)) return false;
  const input = form.elements.namedItem(field);
  if (input instanceof HTMLInputElement) {
    input.setCustomValidity(`${currency} allows up to ${currencyMinorUnits(currency)} decimal places.`);
    input.reportValidity();
  }
  return true;
}
