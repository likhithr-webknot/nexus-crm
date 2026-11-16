import { LeadRecord } from "@/components/leads/lead-record";

export default async function LeadPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <LeadRecord id={id} />;
}
