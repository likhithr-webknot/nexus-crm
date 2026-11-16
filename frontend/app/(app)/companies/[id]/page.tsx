import { CompaniesWorkspace } from "@/components/companies/companies-workspace";

export default async function CompanyPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <CompaniesWorkspace recordId={id} />;
}
