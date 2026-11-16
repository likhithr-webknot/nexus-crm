import { TeamView } from "@/components/team/team-view";
import { TEAM } from "@/lib/mock-data";

export const metadata = { title: "Team Management" };

export default function TeamPage() {
  return <TeamView members={TEAM} />;
}
