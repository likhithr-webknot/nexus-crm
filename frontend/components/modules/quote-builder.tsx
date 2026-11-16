"use client";

import { useQuery } from "@tanstack/react-query";
import { Eye, Plus, Save, Trash2 } from "lucide-react";
import { useState } from "react";
import { Card, buttonPrimary, buttonSecondary, control } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { modulesApi, type ModuleRecord, type QuoteLine } from "@/lib/modules";
import { localId } from "@/lib/id";

const money = (value: number) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(value);
const lineNet = (line: QuoteLine) => line.quantity * line.unitPrice * (1 - line.discountPct / 100);
const lineTax = (line: QuoteLine) => lineNet(line) * line.taxPct / 100;

export function QuoteBuilder({ quote, onSaved }: { quote: ModuleRecord; onSaved: () => Promise<unknown> }) {
  const [lines, setLines] = useState<QuoteLine[]>(quote.lineItems ?? (quote.amount > 0 ? [{ id: `${quote.id}-initial`, productId: "", name: "Custom line", quantity: 1, unitPrice: quote.amount, discountPct: 0, taxPct: 0 }] : []));
  const [preview, setPreview] = useState(false);
  const [saving, setSaving] = useState(false);
  const products = useQuery({ queryKey: ["workspace", "products"], queryFn: () => modulesApi.list("products") });
  const subtotal = lines.reduce((sum, line) => sum + lineNet(line), 0);
  const taxes = lines.reduce((sum, line) => sum + lineTax(line), 0);
  const total = subtotal + taxes;
  const update = (id: string, changes: Partial<QuoteLine>) => setLines((current) => current.map((line) => line.id === id ? { ...line, ...changes } : line));
  const add = () => {
    const product = products.data?.find((item) => item.status === "Active");
    setLines((current) => [...current, { id: localId(), productId: product?.id ?? "", name: product?.name ?? "Custom line", quantity: 1, unitPrice: product?.amount ?? 0, discountPct: 0, taxPct: 0 }]);
  };
  const save = async () => {
    setSaving(true);
    try { await modulesApi.update("quotes", quote.id, { lineItems: lines, amount: total }); await onSaved(); toast("Quote lines saved."); }
    catch { toast("Quote lines could not be saved. Try again."); }
    finally { setSaving(false); }
  };

  return <Card className="overflow-hidden"><div className="flex flex-wrap items-start justify-between gap-3 border-b border-line p-5"><div><h2 className="text-[15px] font-semibold">Quote builder</h2><p className="mt-1 text-[12px] text-ink-3">Edit products, discounts and tax. Totals recalculate immediately.</p></div><div className="flex gap-2"><button className={buttonSecondary} onClick={() => setPreview((value) => !value)}><Eye className="size-4" />{preview ? "Edit lines" : "Preview"}</button><button className={buttonPrimary} onClick={save} disabled={saving}><Save className="size-4" />{saving ? "Saving…" : "Save quote"}</button></div></div>
    {preview ? <div className="mx-auto my-6 max-w-2xl border border-line bg-surface p-8 shadow-sm"><div className="flex items-start justify-between border-b border-line pb-6"><div><p className="text-[11px] font-semibold tracking-wide text-brand uppercase">Nexus CRM · Quote preview</p><h3 className="mt-2 text-[23px] font-semibold">{quote.name}</h3><p className="mt-1 text-[13px] text-ink-2">Prepared for {quote.account || "your customer"}</p></div><span className="text-[12px] text-ink-3">{quote.status}</span></div><table className="mt-6 w-full text-left text-[12.5px]"><thead><tr className="border-b border-line text-ink-3"><th className="pb-2">Item</th><th className="pb-2 text-right">Qty</th><th className="pb-2 text-right">Unit price</th><th className="pb-2 text-right">Amount</th></tr></thead><tbody>{lines.map((line) => <tr key={line.id} className="border-b border-line"><td className="py-3">{line.name}{line.discountPct > 0 && <span className="ml-1 text-ink-3">({line.discountPct}% off)</span>}</td><td className="py-3 text-right">{line.quantity}</td><td className="py-3 text-right">{money(line.unitPrice)}</td><td className="py-3 text-right">{money(lineNet(line) + lineTax(line))}</td></tr>)}</tbody></table><p className="tnum mt-6 text-right text-[18px] font-semibold">Total {money(total)}</p><p className="mt-6 text-[11px] text-ink-3">Preview only. No document was sent.</p></div> : <><div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left text-[12px]"><thead className="bg-canvas text-[11px] text-ink-3 uppercase"><tr><th className="px-4 py-3">Product</th><th className="px-4 py-3">Quantity</th><th className="px-4 py-3">Unit price</th><th className="px-4 py-3">Discount %</th><th className="px-4 py-3">Tax %</th><th className="px-4 py-3 text-right">Line total</th><th className="px-4 py-3"><span className="sr-only">Remove</span></th></tr></thead><tbody>{lines.map((line) => <tr key={line.id} className="border-t border-line"><td className="px-4 py-2"><select value={line.productId} onChange={(event) => { const product = products.data?.find((item) => item.id === event.target.value); if (product) update(line.id, { productId: product.id, name: product.name, unitPrice: product.amount }); }} className={`${control} w-48 px-2`} aria-label="Product"><option value={line.productId}>{line.name}</option>{products.data?.filter((item) => item.status === "Active" && item.id !== line.productId).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></td><td className="px-4 py-2"><input type="number" min="1" value={line.quantity} onChange={(event) => update(line.id, { quantity: Math.max(1, Number(event.target.value)) })} className={`${control} w-20 px-2`} aria-label={`Quantity for ${line.name}`} /></td><td className="px-4 py-2"><input type="number" min="0" step="0.01" value={line.unitPrice} onChange={(event) => update(line.id, { unitPrice: Math.max(0, Number(event.target.value)) })} className={`${control} w-28 px-2`} aria-label={`Unit price for ${line.name}`} /></td><td className="px-4 py-2"><input type="number" min="0" max="100" value={line.discountPct} onChange={(event) => update(line.id, { discountPct: Math.max(0, Math.min(100, Number(event.target.value))) })} className={`${control} w-20 px-2`} aria-label={`Discount for ${line.name}`} /></td><td className="px-4 py-2"><input type="number" min="0" max="100" value={line.taxPct} onChange={(event) => update(line.id, { taxPct: Math.max(0, Math.min(100, Number(event.target.value))) })} className={`${control} w-20 px-2`} aria-label={`Tax for ${line.name}`} /></td><td className="tnum px-4 py-2 text-right font-medium">{money(lineNet(line) + lineTax(line))}</td><td className="px-4 py-2"><button onClick={() => setLines((current) => current.filter((item) => item.id !== line.id))} aria-label={`Remove ${line.name}`} className="text-ink-3 hover:text-down"><Trash2 className="size-4" /></button></td></tr>)}</tbody></table></div><div className="flex flex-wrap items-end justify-between gap-4 border-t border-line p-5"><button onClick={add} className={buttonSecondary}><Plus className="size-4" />Add line</button><dl className="tnum grid grid-cols-2 gap-x-8 gap-y-1 text-[12.5px]"><dt className="text-ink-3">Subtotal</dt><dd className="text-right">{money(subtotal)}</dd><dt className="text-ink-3">Tax</dt><dd className="text-right">{money(taxes)}</dd><dt className="border-t border-line pt-2 font-semibold">Total</dt><dd className="border-t border-line pt-2 text-right font-semibold">{money(total)}</dd></dl></div></>}
  </Card>;
}
