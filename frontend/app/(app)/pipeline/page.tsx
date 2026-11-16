import { PipelineBoard } from "@/components/pipeline/pipeline-board";
import { DEALS } from "@/lib/mock-data";

export const metadata = { title: "Sales Pipeline" };

export default function PipelinePage() {
  return <PipelineBoard deals={DEALS} />;
}
