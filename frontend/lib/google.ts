/* What happens at Google when a feature is switched off (backend: MailService.disconnectFeature). */

export type Disconnected = {
  /** True when the app's permission was cancelled in the user's Google account. */
  revokedAtGoogle: boolean;
  /** True when another feature (mail, calendar or inbox) still uses the same Google permission. */
  otherFeaturesStillOn: boolean;
};

/** A plain sentence telling the user what was done and, when needed, what is left for them to do. */
export function disconnectMessage(feature: string, result: Disconnected | undefined): string {
  if (result?.revokedAtGoogle) {
    return `${feature} is off, and the app's permission was removed from your Google account.`;
  }
  if (result?.otherFeaturesStillOn) {
    return `${feature} is off. Google keeps one permission for all features, so it stays until you turn off the others too.`;
  }
  return `${feature} is off here, but Google could not be reached to remove the permission. You can remove it at myaccount.google.com/permissions.`;
}
