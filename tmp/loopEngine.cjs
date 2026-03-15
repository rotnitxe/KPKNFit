"use strict";
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

// services/loopEngine.ts
var loopEngine_exports = {};
__export(loopEngine_exports, {
  advanceCycle: () => advanceCycle,
  cancelLoop: () => cancelLoop,
  detectLoopCollisions: () => detectLoopCollisions,
  formatLoopCountdown: () => formatLoopCountdown,
  getCurrentCycle: () => getCurrentCycle,
  getCycleLength: () => getCycleLength,
  getDaysIntoCycle: () => getDaysIntoCycle,
  getLoopTypeEmoji: () => getLoopTypeEmoji,
  getLoopTypeLabel: () => getLoopTypeLabel,
  migrateEventsToLoops: () => migrateEventsToLoops,
  postponeLoop: () => postponeLoop,
  projectLoops: () => projectLoops,
  reactivateLoop: () => reactivateLoop
});
module.exports = __toCommonJS(loopEngine_exports);
function getCycleLength(program) {
  const block = program.macrocycles[0]?.blocks?.[0];
  if (!block) return 1;
  return block.mesocycles.reduce((acc, m) => acc + m.weeks.length, 0) || 1;
}
function getCurrentCycle(program) {
  return program.loopState?.currentCycle ?? 0;
}
function getDaysIntoCycle(program, startDate, now = /* @__PURE__ */ new Date()) {
  const cycleLength = getCycleLength(program);
  const cycleDays = cycleLength * (program.weekDays ?? 7);
  const daysSinceStart = Math.floor((now.getTime() - startDate.getTime()) / (1e3 * 60 * 60 * 24));
  return daysSinceStart % cycleDays;
}
function projectLoops(program, fromCycle, lookAheadCycles = 12) {
  const loops = program.loops || [];
  if (loops.length === 0) return [];
  const cycleLength = getCycleLength(program);
  const cycleDays = cycleLength * (program.weekDays ?? 7);
  const postponed = program.loopState?.postponed || [];
  const cancelled = new Set(program.loopState?.cancelled || []);
  const projections = [];
  for (let cycle = fromCycle; cycle < fromCycle + lookAheadCycles; cycle++) {
    for (const loop of loops) {
      if (cancelled.has(loop.id)) continue;
      const isActive = cycle > 0 && cycle % loop.repeatEveryXLoops === 0;
      const postponement = postponed.find((p) => p.loopId === loop.id && p.fromCycle === cycle);
      if (isActive && !postponement) {
        projections.push({
          loop,
          cycle,
          isPostponed: false,
          isCancelled: false,
          daysUntil: (cycle - fromCycle) * cycleDays,
          weekInCycle: cycleLength
          // Loop fires after last week of cycle
        });
      }
      const deferredHere = postponed.find((p) => p.loopId === loop.id && p.toCycle === cycle);
      if (deferredHere) {
        projections.push({
          loop,
          cycle,
          isPostponed: true,
          isCancelled: false,
          daysUntil: (cycle - fromCycle) * cycleDays,
          weekInCycle: cycleLength
        });
      }
    }
  }
  return projections.sort((a, b) => a.cycle - b.cycle || (b.loop.priority ?? 0) - (a.loop.priority ?? 0));
}
function detectLoopCollisions(projections) {
  const byCycle = /* @__PURE__ */ new Map();
  for (const p of projections) {
    const arr = byCycle.get(p.cycle) || [];
    arr.push(p);
    byCycle.set(p.cycle, arr);
  }
  const collisions = /* @__PURE__ */ new Map();
  for (const [cycle, loops] of byCycle) {
    if (loops.length > 1) collisions.set(cycle, loops);
  }
  return collisions;
}
function postponeLoop(program, loopId, fromCycle) {
  const updated = JSON.parse(JSON.stringify(program));
  if (!updated.loopState) updated.loopState = { currentCycle: 0 };
  if (!updated.loopState.postponed) updated.loopState.postponed = [];
  const loop = updated.loops?.find((l) => l.id === loopId);
  if (!loop) return updated;
  updated.loopState.postponed.push({
    loopId,
    fromCycle,
    toCycle: fromCycle + 1
  });
  return updated;
}
function cancelLoop(program, loopId) {
  const updated = JSON.parse(JSON.stringify(program));
  if (!updated.loopState) updated.loopState = { currentCycle: 0 };
  if (!updated.loopState.cancelled) updated.loopState.cancelled = [];
  if (!updated.loopState.cancelled.includes(loopId)) {
    updated.loopState.cancelled.push(loopId);
  }
  return updated;
}
function reactivateLoop(program, loopId) {
  const updated = JSON.parse(JSON.stringify(program));
  if (updated.loopState?.cancelled) {
    updated.loopState.cancelled = updated.loopState.cancelled.filter((id) => id !== loopId);
  }
  return updated;
}
function advanceCycle(program) {
  const updated = JSON.parse(JSON.stringify(program));
  if (!updated.loopState) updated.loopState = { currentCycle: 0 };
  updated.loopState.currentCycle += 1;
  if (updated.loopState.postponed) {
    updated.loopState.postponed = updated.loopState.postponed.filter(
      (p) => p.toCycle > updated.loopState.currentCycle
    );
  }
  return updated;
}
function migrateEventsToLoops(program) {
  const updated = JSON.parse(JSON.stringify(program));
  const legacyEvents = (updated.events || []).filter((e) => e.repeatEveryXCycles);
  if (legacyEvents.length === 0) return updated;
  if (!updated.loops) updated.loops = [];
  for (const event of legacyEvents) {
    const alreadyMigrated = updated.loops.some((l) => l.title === event.title);
    if (alreadyMigrated) continue;
    updated.loops.push({
      id: event.id || crypto.randomUUID(),
      title: event.title,
      type: event.type || "custom",
      repeatEveryXLoops: event.repeatEveryXCycles,
      durationType: "week",
      sessions: event.sessions
    });
  }
  updated.events = (updated.events || []).filter((e) => !e.repeatEveryXCycles);
  return updated;
}
function formatLoopCountdown(daysUntil) {
  if (daysUntil <= 0) return "Ahora";
  if (daysUntil === 1) return "1 d\xEDa";
  if (daysUntil < 7) return `${daysUntil} d\xEDas`;
  const weeks = Math.floor(daysUntil / 7);
  const days = daysUntil % 7;
  if (days === 0) return `${weeks} sem`;
  return `${weeks}s ${days}d`;
}
function getLoopTypeEmoji(type) {
  switch (type) {
    case "1rm_test":
      return "\u{1F3CB}\uFE0F";
    case "deload":
      return "\u{1F9D8}";
    case "competition":
      return "\u{1F3C6}";
    case "custom":
      return "\u26A1";
    default:
      return "\u{1F504}";
  }
}
function getLoopTypeLabel(type) {
  switch (type) {
    case "1rm_test":
      return "Test 1RM";
    case "deload":
      return "Descarga";
    case "competition":
      return "Competici\xF3n";
    case "custom":
      return "Personalizado";
    default:
      return "Loop";
  }
}
// Annotate the CommonJS export names for ESM import in node:
0 && (module.exports = {
  advanceCycle,
  cancelLoop,
  detectLoopCollisions,
  formatLoopCountdown,
  getCurrentCycle,
  getCycleLength,
  getDaysIntoCycle,
  getLoopTypeEmoji,
  getLoopTypeLabel,
  migrateEventsToLoops,
  postponeLoop,
  projectLoops,
  reactivateLoop
});
