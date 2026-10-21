"""Archive only this task's edits to another agent's untracked feature files.

Product files are read-only. Recovered originals must match the initial byte hashes.
"""
import difflib
import hashlib
import json
from pathlib import Path

here = Path(__file__).resolve().parent
repo = here.parent.parent
before = json.loads((here / 'source-before.json').read_text(encoding='utf-8'))
baseline = {row['path']: row for row in before['files']}
owned = json.loads((here / 'task-source-changes.json').read_text(encoding='utf-8-sig'))


def replace_once(text, new, old):
    assert text.count(new) == 1, repr(new[:100])
    return text.replace(new, old, 1)


def recover(path, text):
    name = Path(path).name
    if name == 'EnduranceSportModels.kt':
        return replace_once(text, 'import kotlinx.serialization.json.encodeToJsonElement',
                            'import kotlinx.serialization.encodeToJsonElement')
    if name == 'HybridProgramPlanner.kt':
        text = replace_once(text, 'import com.example.kpkn.data.models.HybridProtectedSessionKind\n', '')
        start = text.index('    fun prepare(\n')
        end = text.index('    ): HybridProgramPlanResult {\n', start)
        text = text[:start] + '''    fun prepare(
        program: Program,
        weekId: String,
        bindings: List<HybridSessionGoalBinding>,
        searchNodeBudget: Int = HybridWeekPlanRequest.DEFAULT_SEARCH_NODE_BUDGET,
''' + text[end:]
        start = text.index('        val protected = adaptation?.context?.protectedSessions.orEmpty()\n')
        end = text.index('        val plannerResult = HybridWeekPlanner.plan(\n', start)
        text = text[:start] + text[end:]
        text = replace_once(text, 'candidateSessions = candidates.filter { it.id !in protectedIds },', 'candidateSessions = candidates,')
        text = replace_once(text, 'weeklyGoalRequirements = adaptation?.adjustedRequirements ?: configurationSnapshot.weeklyGoalRequirements,',
                            'weeklyGoalRequirements = configurationSnapshot.weeklyGoalRequirements,')
        text = replace_once(text, '''                fixedSessionAssignments = fixedAssignments,
                coveredGoalIds = coveredGoalIds,
                minimumStartMinuteByWeekday = adaptation?.minimumStarts.orEmpty(),
                weekStartDay = anchor?.dayOfWeek?.value ?: 1,
''', '')
        text = replace_once(text, ' { it.session.goalId } + coveredGoalIds\n', ' { it.session.goalId }\n')
        start = text.index('        val occurrenceContext = adaptation?.context?.let { context ->\n')
        end = text.index('        val proposal = HybridWeekProposal(\n', start)
        text = text[:start] + text[end:]
        text = replace_once(text, '''            scope = if (adaptation == null) HybridProposalScope.TEMPLATE_BASE_SESSIONS
                else HybridProposalScope.ACTIVE_OCCURRENCE_REMAINING_SESSIONS,''',
                            '            scope = HybridProposalScope.TEMPLATE_BASE_SESSIONS,')
        return replace_once(text, '''            trainingAssessment = occurrenceContext?.let { HybridRemainingWeekPlanner.assessment(it, configurationSnapshot) }
                ?: plannerResult.trainingAssessment,
            occurrenceContext = occurrenceContext,''', '            trainingAssessment = plannerResult.trainingAssessment,')
    if name == 'HybridRemainingWeekPlanner.kt':
        start = text.index('        val adapted = try {\n')
        end = text.index('            is HybridProgramPlanResult.Ready ->', start)
        return text[:start] + '''        return when (val plan = HybridProgramPlanner.prepareAdapted(program, week.id, bindings,
            HybridPlanningAdaptation(context, adjusted, starts), searchNodeBudget)) {
''' + text[end:]
    if name == 'HybridProgramPlannerTest.kt':
        for entry in ('HybridCompletedActivityEvidence', 'HybridOccurrenceIdentity', 'HybridOccurrencePlanningContext',
                      'HybridProtectedSessionEvidence', 'HybridProtectedSessionKind', 'HybridProposalScope',
                      'HybridRecordedDurationSource'):
            text = replace_once(text, f'import com.example.kpkn.data.models.{entry}\n', '')
        start = text.index('class HybridProgramPlannerTest {\n') + len('class HybridProgramPlannerTest {\n')
        end = text.index('    @Test\n    fun preparesOnlyExplicitlyBoundBaseSessionsAndPreservesTheProgram()', start)
        text = text[:start] + text[end:]
        return replace_once(text, '''            intent = TrainingIntent(
                goals = validIntent(includeRunning = true).goals.filter { it.id == "running" },
                primaryGoalId = "running",
            ),''', '            intent = validIntent(includeRunning = true),')
    if name == 'HybridSessionDurationPolicyTest.kt':
        return replace_once(text, 'checkNotNull(resolution.estimatedMinutes) < resolution.placementMinutes',
                            'resolution.estimatedMinutes < resolution.placementMinutes')
    if name == 'CombatSportEngineTest.kt':
        text = replace_once(text, 'import kotlinx.serialization.json.jsonObject\n', '')
        text = replace_once(text, 'payload = json.encodeToJsonElement(plan).jsonObject,', 'payload = json.encodeToJsonElement(plan),')
        text = replace_once(text, 'document.copy(payload = json.encodeToJsonElement(forgedSport).jsonObject)',
                            'document.copy(payload = json.encodeToJsonElement(forgedSport))')
        return replace_once(text, 'CombatRoundStatus.COMPLETED, 20)))).jsonObject\n', 'CombatRoundStatus.COMPLETED, 20))))\n')
    if name == 'FieldSportEngineTest.kt':
        text = replace_once(text, 'val encoded = json.encodeToJsonElement(plan).jsonObject', 'val encoded = json.encodeToJsonElement(plan)')
        return replace_once(text, 'payload = json.encodeToJsonElement(plan.copy(sportId = FieldSportDiscipline.SPRINT)).jsonObject,',
                            'payload = json.encodeToJsonElement(plan.copy(sportId = FieldSportDiscipline.SPRINT)),')
    if name == 'StrengthSportEngineTest.kt':
        text = replace_once(text, 'import kotlinx.serialization.json.jsonObject\n', '')
        return replace_once(text, 'payload = json.encodeToJsonElement(payload).jsonObject,', 'payload = json.encodeToJsonElement(payload),')
    if name == 'SportTrackingRoomTest.kt':
        start = text.index('        val database = repo.databaseForTests()\n')
        end = text.index('\n    }\n}', start)
        return text[:start] + '''        repo.databaseForTests().close()
        assertTrue(service.upsertCompletedResults("existing", listOf(doc("valid", SportDocumentPurpose.RESULT))) is SportTrackingSaveResult.StorageFailure)
        assertEquals(before, repo.history.value)''' + text[end:]
    raise AssertionError(name)


patches, manifest = [], []
for row in owned:
    relative = row['path']
    current = (repo / relative).read_bytes()
    assert hashlib.sha256(current).hexdigest() == row['sha256'], relative
    text = current.decode('utf-8').replace('\r\n', '\n')
    old = recover(relative, text)
    candidates = [old.encode('utf-8'), old.replace('\n', '\r\n').encode('utf-8')]
    original = next((candidate for candidate in candidates
                     if hashlib.sha256(candidate).hexdigest() == baseline[relative]['sha256']), None)
    assert original is not None, f'Original byte hash did not match: {relative}'
    patches.append(f'diff --git a/{relative} b/{relative}\n')
    patches.extend(difflib.unified_diff(old.splitlines(keepends=True), text.splitlines(keepends=True),
                                      fromfile='a/' + relative, tofile='b/' + relative))
    manifest.append({'path': relative, 'before_sha256': baseline[relative]['sha256'],
                     'after_sha256': row['sha256'], 'original_recovered_and_hash_verified': True})
(here / 'scoped-source-fixes.patch').write_text(''.join(patches), encoding='utf-8', newline='\n')
(here / 'scoped-source-fixes.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print(json.dumps({'files': len(manifest), 'all_original_hashes_verified': True,
                  'patch_bytes': (here / 'scoped-source-fixes.patch').stat().st_size}))
