import { FollowUpsView } from "@/components/follow-ups/follow-ups-view";
import { FOLLOW_UPS } from "@/lib/mock-data";

export const metadata = { title: "Follow-ups" };

export default function FollowUpsPage() {
  return <FollowUpsView followUps={FOLLOW_UPS} />;
}
