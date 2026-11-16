import { ModuleWorkspace } from "@/components/modules/module-workspace";

// The same screen as /communications: the workspace reads the record to open from the address.
export default function Page() {
  return <ModuleWorkspace kind="communications" />;
}
