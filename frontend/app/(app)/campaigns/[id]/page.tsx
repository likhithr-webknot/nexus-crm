import { ModuleWorkspace } from "@/components/modules/module-workspace";
export default async function Page({ params }: { params: Promise<{ id: string }> }) { return <ModuleWorkspace kind="campaigns" recordId={(await params).id} />; }
