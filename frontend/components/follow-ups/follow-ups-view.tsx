"use client";

import { LoadFailed } from "@/components/ui/load-state";
import { Card, Page, PageHeader, buttonPrimary } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { monthGrid, monthOf, shiftMonth } from "@/lib/calendar";
import { formatDay, localIso, todayLocal } from "@/lib/format";
import { Plus } from "lucide-react";
import { useState } from "react";
import { FollowUpAgenda } from "./follow-up-agenda";
import { FollowUpCalendar } from "./follow-up-calendar";
import { dayOf } from "./follow-up-item";
import { FollowUpTiles } from "./follow-up-tiles";
import { NewFollowUpDialog } from "./new-follow-up-dialog";
import { useFollowUps, useToggleFollowUp } from "./use-follow-ups";

/** The follow-ups page: a calendar, the agenda of the picked day, and the counts above them. */
export function FollowUpsView() {
  const today = todayLocal();
  const [adding, setAdding] = useState(false);
  const [selected, setSelected] = useState(today);
  const [month, setMonth] = useState(() => monthOf(today));

  const grid = monthGrid(month);
  const data = useFollowUps(grid, today);
  const toggle = useToggleFollowUp();

  const showDay = (iso: string) => {
    setSelected(iso);
    setMonth(monthOf(iso));
  };

  if (data.calendar.isError && !data.calendar.data) {
    return (
      <Page>
        <PageHeader title="Follow-ups" subtitle="Stay on top of every customer conversation." />
        <Card>
          <LoadFailed what="follow-ups" onRetry={() => data.calendar.refetch()} />
        </Card>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader title="Follow-ups" subtitle="Stay on top of every customer conversation.">
        <button className={buttonPrimary} onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden />
          New follow-up
        </button>
      </PageHeader>

      <FollowUpTiles summary={data.summary} />

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <FollowUpCalendar
          month={month}
          grid={grid}
          items={data.items}
          today={today}
          selected={selected}
          onSelect={setSelected}
          onToday={() => showDay(today)}
          onShift={(by) => setMonth((m) => shiftMonth(m, by))}
        />
        <FollowUpAgenda
          day={selected}
          isToday={selected === today}
          items={data.items.filter((f) => dayOf(f) === selected && f.status !== "CANCELLED")}
          overdue={data.overdue}
          onToggle={(item) => toggle.mutate(item)}
        />
      </div>

      <NewFollowUpDialog
        open={adding}
        onClose={() => setAdding(false)}
        defaultDay={selected}
        onAdded={(followUp) => {
          // Show the day it landed on, so the new entry is visible straight away.
          const local = localIso(followUp.dueAt);
          showDay(local.slice(0, 10));
          toast(`Follow-up scheduled for ${formatDay(local)}.`);
        }}
      />
    </Page>
  );
}
