import { Suspense } from "react";
import { SettingsView } from "@/components/settings/settings-view";

export const metadata = { title: "Settings" };

export default function SettingsPage({ searchParams }: PageProps<"/settings">) {
  return (
    // ?section= is only known at request time, so the part that reads it streams in.
    <Suspense fallback={null}>
      <Settings searchParams={searchParams} />
    </Suspense>
  );
}

async function Settings({ searchParams }: Pick<PageProps<"/settings">, "searchParams">) {
  const { section } = await searchParams;
  return <SettingsView initialSection={typeof section === "string" ? section : undefined} />;
}
