package app.hushmessenger.patches.controls

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val WATCHER = "LX/7TX;->A8Y(Landroid/text/Editable;Z)V"
private const val LOOKUP = "const/16 v0, 0x289\ninvoke-static {v0}, LX/46q;->A00(I)Ljava/lang/String;\nmove-result-object v0"

/**
 * The search mode comes from a generated switch table, asked by number. The plain mode jumps back to the one call that
 * hands the mode over.
 */
private fun watcher(
    call: String = "invoke-interface {v1, v0}, LX/H7o;->DUX(Ljava/lang/String;)V",
    id: String = WATCHER,
    search: String = LOOKUP,
) = fixtureMethod(id, """
    const-string v2, "afterTextChanged"
    iget-object v1, p0, LX/7Sa;->A05:LX/H7o;
    if-eqz v10, :plain
    $search
    :call
    $call
    goto :done
    :plain
    const-string v0, "expression"
    goto :call
    :done
    return-void
""".trimIndent(), registers = 12)

/** The watcher for the catalog-wide discovery fixture. */
internal fun emojiSearchFixture() = listOf(fixtureClass("LX/7TX;", listOf(watcher())))

private fun Method.code() = implementation!!.instructions.toList()

/**
 * The call gains the helper pair right before it, so the search path runs through the extension. The plain path's jump
 * still lands on the original call, past the pair, because it already holds the plain mode.
 */
private fun assertSearchHook(before: List<Instruction>, after: List<Instruction>, call: Int, label: String) {
    val mode = (before[call] as FiveRegisterInstruction).registerD
    assertEquals(before.size + 2, after.size, label)
    assertEquals(before, after.filterIndexed { i, _ -> i != call && i != call + 1 }, label)
    val helper = after[call] as FiveRegisterInstruction
    assertEquals(Opcode.INVOKE_STATIC, helper.opcode, label)
    assertEquals(EMOJI_SEARCH_HELPER, (helper as ReferenceInstruction).reference.toString(), label)
    assertEquals(listOf(1, mode), listOf(helper.registerCount, helper.registerC), label)
    assertEquals(Opcode.MOVE_RESULT_OBJECT, after[call + 1].opcode, label)
    assertEquals(mode, (after[call + 1] as OneRegisterInstruction).registerA, label)
    assertEquals(before[call], after[call + 2], label)
    val plainJump = after.indices.first { after[it].opcode == Opcode.GOTO && after.branchTarget(it) == call + 2 }
    // 582's string pools pass 65535 entries, so the plain mode's name can load as const-string/jumbo.
    assertTrue(after[plainJump - 1].opcode in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO), label)
}

class EmojiSearchTest {
    @AfterTest fun reset() { activeProfile = SYNTHETIC_PROFILE }

    private fun found(vararg methods: MutableMethod) =
        findControls(methods.map { fixtureClass(it.definingClass, listOf(it)) }).getValue(EMOJI_SEARCH)

    @Test fun theTableLookupGoesThroughTheExtensionAndNothingStockMoves() {
        val method = watcher()
        assertEquals(listOf(method.hookId()), found(method).map { it.hookId() })
        validateControls(findControls(listOf(fixtureClass(method.definingClass, listOf(method)))), setOf(EMOJI_SEARCH))
        val before = method.code()
        val call = method.emojiSearchCall()
        injectControl(EMOJI_SEARCH, mapOf(EMOJI_SEARCH to listOf(method)))
        assertSearchHook(before, method.code(), call, "watcher")
        // Two methods that look like the watcher are ambiguous, so neither is hooked.
        assertTrue(found(watcher(), watcher(id = "LX/7GE;->A8e(Landroid/text/Editable;Z)V")).isEmpty())
    }

    @Test fun aShapeThatMovedStopsBeforeAnyEdit() {
        val shapes = mapOf(
            // An older release loaded the search mode as a literal. Every supported build asks the table.
            "search mode loaded as a literal" to watcher(search = "const-string v0, \"expression_search\""),
            "lookup takes no number" to watcher(search = LOOKUP.replace("{v0}, LX/46q;->A00(I)", "{}, LX/46q;->A00()")),
            "lookup answers another register" to watcher(search = LOOKUP.replace("move-result-object v0", "move-result-object v2")),
            "call takes an Object" to watcher("invoke-interface {v1, v0}, LX/H7o;->DUX(Ljava/lang/Object;)V"),
            "call returns a value" to watcher("invoke-interface {v1, v0}, LX/H7o;->DUX(Ljava/lang/String;)Z"),
            "call is static" to watcher("invoke-static {v1, v0}, LX/H7o;->DUX(Ljava/lang/String;)V"),
            "call has a third argument" to watcher("invoke-interface {v1, v0, v2}, LX/H7o;->DUX(Ljava/lang/String;)V"),
            "another jump into the call" to fixtureMethod(WATCHER, """
                const-string v2, "afterTextChanged"
                if-eqz v10, :plain
                $LOOKUP
                :call
                invoke-interface {v1, v0}, LX/H7o;->DUX(Ljava/lang/String;)V
                return-void
                :plain
                if-eqz v3, :call
                const-string v0, "expression"
                goto :call
            """.trimIndent(), registers = 12),
            // A switch case isn't a branch instruction's target, so it needs its own check.
            "a switch case lands on the call" to fixtureMethod(WATCHER, """
                const-string v2, "afterTextChanged"
                iget-object v1, p0, LX/7Sa;->A05:LX/H7o;
                packed-switch v3, :cases
                if-eqz v10, :plain
                $LOOKUP
                :call
                invoke-interface {v1, v0}, LX/H7o;->DUX(Ljava/lang/String;)V
                goto :done
                :plain
                const-string v0, "expression"
                goto :call
                :done
                return-void
                :cases
                .packed-switch 0x1
                :call
                .end packed-switch
            """.trimIndent(), registers = 12),
            "plain mode doesn't jump" to fixtureMethod(WATCHER, """
                const-string v2, "afterTextChanged"
                const-string v0, "expression"
                $LOOKUP
                invoke-interface {v1, v0}, LX/H7o;->DUX(Ljava/lang/String;)V
                return-void
            """.trimIndent(), registers = 12),
        )
        for ((case, bad) in shapes) {
            val good = watcher(id = "LX/7GF;->A8e(Landroid/text/Editable;Z)V")
            val before = good.code()
            val failure = assertFailsWith<PatchException>(case) {
                injectControl(EMOJI_SEARCH, mapOf(EMOJI_SEARCH to listOf(good, bad)))
            }
            assertContains(failure.message.orEmpty(), "emoji search mode call", message = case)
            assertEquals(before, good.code(), case)
        }
    }

    @Test fun everySupportedBuildHooksItsOneTextWatcher() {
        val root = System.getenv("HUSH_NATIVE_FIXTURES")
        assumeTrue(root != null, "Set HUSH_NATIVE_FIXTURES to the exact stock fixture directory")
        val apks = Files.list(Path.of(root!!)).use { it.filter { p -> p.toString().endsWith(".apk") }.sorted().toList() }
        assertEquals(controlProfiles.size, apks.size)
        for (apk in apks) {
            val code = apk.fileName.toString().substringBeforeLast(".apk").substringAfterLast('-')
            activeProfile = controlProfileFor(code)
            val dex = DexFileFactory.loadDexContainer(apk.toFile(), Opcodes.forApi(35))
            val watchers = findEmojiSearch(dex.dexEntryNames.flatMap { dex.getEntry(it)!!.dexFile.classes })
            assertEquals(activeProfile.hooks.getValue(EMOJI_SEARCH), watchers.map { it.hookId() }.toSet(), code)
            validateControls(mapOf(EMOJI_SEARCH to watchers), setOf(EMOJI_SEARCH))
            val method = MutableMethod(watchers.single())
            val before = method.code()
            val call = method.emojiSearchCall()
            method.injectEmojiSearch()
            assertSearchHook(before, method.code(), call, code)
        }
    }
}
