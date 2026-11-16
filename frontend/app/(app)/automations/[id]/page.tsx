import { ModuleWorkspace } from "@/components/modules/module-workspace";

// The same screen as /automations: the workspace reads the record to open from the address.
export default function Page() {
  return <ModuleWorkspace kind="automations" />;
}
