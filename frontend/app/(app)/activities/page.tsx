import { ActivitiesView } from "@/components/activities/activities-view";
import { ACTIVITIES } from "@/lib/mock-data";

export const metadata = { title: "Activities" };

export default function ActivitiesPage() {
  return <ActivitiesView activities={ACTIVITIES} />;
}
