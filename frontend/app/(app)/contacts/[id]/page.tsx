import { ContactRecord } from "@/components/contacts/contact-record";

export default async function ContactPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ContactRecord id={id} />;
}
