import { ContactsView } from "@/components/contacts/contacts-view";
import { CONTACTS } from "@/lib/mock-data";

export const metadata = { title: "Contacts" };

export default function ContactsPage() {
  return <ContactsView contacts={CONTACTS} />;
}
