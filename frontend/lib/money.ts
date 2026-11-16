/** An amount as the API sends it: the number is a string, so it never loses cents. */
export type Money = { amount: string; currency: string };

export const moneyValue = (money: Money | null | undefined) => (money ? Number(money.amount) : 0);
