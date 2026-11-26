package app.hushmessenger.patches.controls

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The pinned messages loader and a second banner behind a gate of the same shape, as 587 names them. */
private const val PB_LOADER = "LX/4hP;"
private const val PB_OTHER = "LX/AUm;"
private const val PB_HOOK_SIZE = 2

internal const val SYNTHETIC_PINNED_BAR = "$BANNER_FETCH->A02()LX/5aW;"

/** The pinned messages banner and another banner, as 588.1 (347617503) names them, and the class they share for a banner. */
private const val PB_MODEL = "LX/5hp;"
private const val PB_OTHER_MODEL = "LX/5ce;"
private const val PB_BANNER = "LX/DV9;"
private const val PB_PRIORITIZER = "LX/5W2;"
private const val PB_PARSER = "LX/Bzb;"
private const val PB_RANK_HOOK_SIZE = 3

internal const val SYNTHETIC_PINNED_BAR_FIRST = "$PB_PRIORITIZER->A00($PB_BANNER)I"

/** A banner loader whose static initializer names [source], keeping the chat's thread key and [models] in fields. */
private fun pbLoader(type: String = PB_LOADER, source: String = PINNED_BAR_SOURCE, models: List<String> = emptyList()) = fixtureClass(type, listOf(
    fixtureMethod("$type-><clinit>()V", "const-string v0, \"$source\"\nreturn-void", registers = 1,
        flags = AccessFlags.STATIC.value or AccessFlags.CONSTRUCTOR.value),
), extraFields = (models + THREAD_KEY_TYPE).mapIndexed { i, held ->
    ImmutableField(type, "A0$i", held, AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null, null)
})

/** A banner class built from one open banner row. */
private fun pbModel(type: String) = fixtureClass(type, listOf(
    fixtureMethod("$type-><init>(LX/CSF;)V", "invoke-direct {v0}, Ljava/lang/Object;-><init>()V\nreturn-void", registers = 2,
        flags = AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value),
))

/**
 * The open banners' mapper cut down: v0 is the row's banner type, and type 0x3dc builds [pinned], 0x3e3 [other]. v4 is
 * the row.
 */
private fun pbParser(type: String = PB_PARSER, pinned: String = PB_MODEL, other: String = PB_OTHER_MODEL) = fixtureClass(type, listOf(
    fixtureMethod("$type->onCompletion(Ljava/lang/Object;)V", """
        const/4 v0, 0x0
        const/16 v1, 0x3dc
        if-eq v0, v1, :pinned
        const/16 v1, 0x3e3
        if-eq v0, v1, :other
        return-void
        :pinned
        new-instance v2, $pinned
        invoke-direct {v2, v4}, $pinned-><init>(LX/CSF;)V
        return-void
        :other
        new-instance v2, $other
        invoke-direct {v2, v4}, $other-><init>(LX/CSF;)V
        return-void
    """.trimIndent(), registers = 5),
))

/**
 * DefaultBannerPrioritizer's rank as 588 has it: v3 is the prioritizer and v4 the banner, a null banner and one missing
 * from the map rank last, and the rank's intValue is the return. [overwrite] reuses the banner's register for the log,
 * [jumpOntoReturn] lands a branch on the rank's return.
 */
private fun pbPrioritizer(id: String = SYNTHETIC_PINNED_BAR_FIRST, owner: String = BANNER_PRIORITIZER, overwrite: Boolean = false,
    jumpOntoReturn: Boolean = false, registers: Int = 5, flags: Int = AccessFlags.PUBLIC.value) = fixtureMethod(id, listOfNotNull(
    "const v2, 0x7fffffff",
    "if-eqz v2, :ranked".takeIf { jumpOntoReturn },
    "if-eqz v4, :none",
    "iget-object v0, v3, $PB_PRIORITIZER->A01:Ljava/util/Map;",
    "invoke-virtual {v4}, Ljava/lang/Object;->getClass()Ljava/lang/Class;",
    "move-result-object v1",
    "invoke-static {v1, v0}, LX/18k;->A0m(Ljava/lang/Object;Ljava/util/Map;)Ljava/lang/Number;",
    "move-result-object v0",
    "if-eqz v0, :missing",
    "invoke-virtual {v0}, Ljava/lang/Number;->intValue()I",
    "move-result v0",
    ":ranked".takeIf { jumpOntoReturn },
    "return v0",
    ":missing",
    "const-string v0, \"$BANNER_RANK_MISSING\"",
    "invoke-static {v0, v1}, LX/002;->A0N(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;",
    "move-result-object v1",
    "move-object v4, v1".takeIf { overwrite },
    "const-string v0, \"$owner\"",
    "invoke-static {v0, v1}, LX/0sS;->A0G(Ljava/lang/String;Ljava/lang/String;)V",
    ":none",
    "return v2",
).joinToString("\n"), registers = registers, flags = flags)

/** The prioritizer, the mapper and both banners, for a loader holding the pinned banner. */
private fun pinnedBarFirstFixture(prioritizer: MutableMethod = pbPrioritizer()): List<MutableClass> =
    listOf(fixtureClass(PB_PRIORITIZER, listOf(prioritizer)), pbParser(), pbModel(PB_MODEL), pbModel(PB_OTHER_MODEL))

/**
 * The fetch cut down to two gated banners: a thread key check that jumps straight to the banner, and otherwise a second
 * check that falls into it. v1 is the list, v2 the session and v3 the thread key.
 */
private fun pbFetch(first: String = PB_LOADER, second: String = PB_OTHER, check: String = "A1K") = fixtureMethod(SYNTHETIC_PINNED_BAR, """
    new-instance v1, Ljava/util/ArrayList;
    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V
    invoke-virtual {v3}, $THREAD_KEY_TYPE->$check()Z
    move-result v0
    if-nez v0, :first
    invoke-virtual {v3}, $THREAD_KEY_TYPE->A1r()Z
    move-result v0
    if-eqz v0, :second_gate
    :first
    new-instance v0, $first
    invoke-direct {v0, v2, v3}, $first-><init>($FB_USER_SESSION$THREAD_KEY_TYPE)V
    invoke-virtual {v1, v0}, Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z
    :second_gate
    invoke-virtual {v3}, $THREAD_KEY_TYPE->A1q()Z
    move-result v0
    if-nez v0, :second
    const/4 v0, 0x0
    if-eqz v0, :done
    :second
    new-instance v0, $second
    invoke-direct {v0, v2, v3}, $second-><init>($FB_USER_SESSION$THREAD_KEY_TYPE)V
    invoke-virtual {v1, v0}, Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z
    :done
    const/4 v0, 0x0
    return-object v0
""".trimIndent(), registers = 5)

internal fun pinnedBarFixture(): List<MutableClass> =
    listOf(fixtureClass(BANNER_FETCH, listOf(pbFetch())), pbLoader(models = listOf(PB_MODEL)), pbLoader(PB_OTHER, "TranslationBannerDataSource")) +
        pinnedBarFirstFixture()

private fun MutableMethod.pbCode() = implementation!!.instructions.toList()

private fun Instruction.pbReference() = (this as? ReferenceInstruction)?.reference?.toString()

class PinnedBarTest {
    @AfterTest fun forget() {
        pinnedBarLoader = null
        pinnedBarModel = null
    }

    private fun hook(method: MutableMethod) {
        pinnedBarLoader = PB_LOADER
        injectControl(PINNED_BAR, mapOf(PINNED_BAR to listOf(method)))
    }

    private fun hookRank(method: MutableMethod) {
        pinnedBarModel = PB_MODEL
        injectControl(PINNED_BAR_FIRST, mapOf(PINNED_BAR_FIRST to listOf(method)))
    }

    @Test fun theRankFinderAndItsMirrorTakeTheStockPrioritizer() {
        assertEquals(PB_MODEL, resolvePinnedBarModel(pinnedBarFixture()))
        assertEquals(listOf(SYNTHETIC_PINNED_BAR_FIRST), findPinnedBarFirst(pinnedBarFixture()).map { it.hookId() })
        assertEquals(listOf(SYNTHETIC_PINNED_BAR_FIRST), mirroredControls(pinnedBarFixture()).getValue(PINNED_BAR_FIRST).map { it.hookId() })
        // The other banner the loader might keep isn't built for 0x3dc, so the pinned one is still the only one.
        val both = pinnedBarFixture().filter { it.type != PB_LOADER } + pbLoader(models = listOf(PB_OTHER_MODEL, PB_MODEL))
        assertEquals(PB_MODEL, resolvePinnedBarModel(both))
    }

    /** The pinned banner's rank goes through the extension between the rank's move-result and the return. */
    @Test fun thePinnedBannersRankGoesThroughTheExtensionRightBeforeTheReturn() {
        val method = pbPrioritizer()
        val before = method.pbCode()
        val read = before.indexOfFirst { it.pbReference() == "Ljava/lang/Number;->intValue()I" }
        // The banner is the method's one parameter, and the rank is what the stock move-result writes.
        val banner = method.implementation!!.registerCount - 1
        val rank = (before[read + 1] as OneRegisterInstruction).registerA
        assertEquals(Opcode.RETURN, before[read + 2].opcode)
        hookRank(method)
        val code = method.pbCode()
        assertEquals(before.size + PB_RANK_HOOK_SIZE, code.size)
        val test = code[read + 2] as TwoRegisterInstruction
        assertEquals(Opcode.INSTANCE_OF, code[read + 2].opcode)
        assertEquals(listOf(banner, banner), listOf(test.registerA, test.registerB))
        assertEquals(PB_MODEL, code[read + 2].pbReference())
        val call = code[read + 3] as FiveRegisterInstruction
        assertEquals(PINNED_BAR_RANK_CALL, code[read + 3].pbReference())
        assertEquals(listOf(banner, rank), listOf(call.registerC, call.registerD))
        assertEquals(2, call.registerCount)
        assertEquals(Opcode.MOVE_RESULT, code[read + 4].opcode)
        assertEquals(rank, (code[read + 4] as OneRegisterInstruction).registerA)
        assertEquals(Opcode.RETURN, code[read + 5].opcode)
        assertEquals(rank, (code[read + 5] as OneRegisterInstruction).registerA)
        // A null banner still ranks last at the method's end, and one missing from the map still gets logged.
        assertEquals(code.lastIndex, code.branchTarget(1))
        assertEquals(BANNER_RANK_MISSING, ((code[code.branchTarget(read - 1)] as ReferenceInstruction).reference as StringReference).string)
        assertTrue(method.jumpTargets().none { it in read + 2..read + 5 })
        assertEquals(before.map { it.opcode }, code.filterIndexed { i, _ -> i !in read + 2..read + 4 }.map { it.opcode })
    }

    @Test fun aSecondPassLeavesTheRankHookAsItIs() {
        val method = pbPrioritizer()
        hookRank(method)
        val size = method.pbCode().size
        assertTrue(method.validatePinnedBarFirst().hooked)
        hookRank(method)
        assertEquals(size, method.pbCode().size)
        assertEquals(1, method.pbCode().count { it.pbReference() == PINNED_BAR_RANK_CALL })
    }

    @Test fun onlyTheStockRankShapeIsTaken() {
        val stock = pinnedBarFixture()
        fun withLoader(loader: MutableClass) = stock.filter { it.type != PB_LOADER } + loader
        fun withPrioritizer(method: MutableMethod) = stock.filter { it.type != PB_PRIORITIZER } + fixtureClass(PB_PRIORITIZER, listOf(method))
        fun none(classes: List<MutableClass>) {
            assertTrue(findPinnedBarFirst(classes).isEmpty())
            assertTrue(mirroredControls(classes).getValue(PINNED_BAR_FIRST).isEmpty())
        }
        // The pinned loader keeps no banner built for 0x3dc, or keeps two of them.
        assertEquals(null, resolvePinnedBarModel(withLoader(pbLoader())))
        none(withLoader(pbLoader()))
        val two = withLoader(pbLoader(models = listOf(PB_MODEL, PB_OTHER_MODEL))) + pbParser("LX/Bzc;", pinned = PB_OTHER_MODEL, other = PB_MODEL)
        assertEquals(null, resolvePinnedBarModel(two))
        none(two)
        // The banner's register gets reused, something lands on the rank's return, or the banner sits above v15.
        for (changed in listOf(pbPrioritizer(overwrite = true), pbPrioritizer(jumpOntoReturn = true), pbPrioritizer(registers = 20))) {
            assertEquals(null, changed.bannerRankSite(PB_MODEL))
            none(withPrioritizer(changed))
        }
        // Only one of the two strings, or a second method logging both.
        none(withPrioritizer(pbPrioritizer(owner = "OtherPrioritizer")))
        none(stock + fixtureClass("LX/5W3;", listOf(pbPrioritizer("LX/5W3;->A00($PB_BANNER)I"))))
        // A static method, whose last register is its only parameter and no banner of a prioritizer.
        assertEquals(null, pbPrioritizer(flags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value).bannerRankSite(PB_MODEL))
        // Without the banner found with the hooks there's nothing to go on. The finds above leave this build's banner set.
        pinnedBarModel = null
        assertFailsWith<PatchException> { pbPrioritizer().validatePinnedBarFirst() }
    }

    @Test fun theFinderAndItsMirrorTakeTheStockFetch() {
        assertEquals(PB_LOADER, resolvePinnedBarLoader(pinnedBarFixture()))
        assertEquals(setOf(SYNTHETIC_PINNED_BAR), findPinnedBar(pinnedBarFixture()).map { it.hookId() }.toSet())
        assertEquals(setOf(SYNTHETIC_PINNED_BAR), mirroredControls(pinnedBarFixture()).getValue(PINNED_BAR).map { it.hookId() }.toSet())
    }

    /** The pinned banner's own check goes through the extension; the other banner's check, of the same shape, doesn't. */
    @Test fun onlyThePinnedBannersCheckGoesThroughTheExtension() {
        val method = pbFetch()
        val before = method.pbCode()
        hook(method)
        val code = method.pbCode()
        assertEquals(before.size + PB_HOOK_SIZE, code.size)
        assertEquals("$THREAD_KEY_TYPE->A1K()Z", code[2].pbReference())
        assertEquals(Opcode.MOVE_RESULT, code[3].opcode)
        assertEquals(PINNED_BAR_CALL, code[4].pbReference())
        assertEquals(listOf(0), (code[4] as RegisterRangeInstruction).let { it.startRegister until it.startRegister + it.registerCount }.toList())
        assertEquals(Opcode.MOVE_RESULT, code[5].opcode)
        assertEquals(0, (code[5] as OneRegisterInstruction).registerA)
        assertEquals(Opcode.IF_NEZ, code[6].opcode)
        assertEquals(1, code.count { it.pbReference() == PINNED_BAR_CALL })
        // The branch still lands on the pinned loader.
        assertEquals(PB_LOADER, code[code.branchTarget(6)].pbReference())
        assertEquals(before.map { it.opcode }, code.filterIndexed { i, _ -> i != 4 && i != 5 }.map { it.opcode })
    }

    /** 588's fetch, cut down: one thread key check whose no skips the pinned loader with the if-eqz right above it. */
    private fun pb588Fetch(extraJump: Boolean = false) = fixtureMethod(SYNTHETIC_PINNED_BAR, """
        new-instance v1, Ljava/util/ArrayList;
        invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V
        ${if (extraJump) "if-nez v2, :made" else "nop"}
        invoke-virtual {v3}, $THREAD_KEY_TYPE->A1J()Z
        move-result v0
        if-eqz v0, :skip
        :made
        new-instance v0, $PB_LOADER
        invoke-direct {v0, v2, v3}, $PB_LOADER-><init>($FB_USER_SESSION$THREAD_KEY_TYPE)V
        invoke-virtual {v1, v0}, Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z
        :skip
        const/4 v0, 0x0
        return-object v0
    """.trimIndent(), registers = 5)

    @Test fun a588FetchThatSkipsTheLoaderOnANoGoesThroughTheExtension() {
        val classes = listOf(fixtureClass(BANNER_FETCH, listOf(pb588Fetch())), pbLoader())
        assertEquals(setOf(SYNTHETIC_PINNED_BAR), findPinnedBar(classes).map { it.hookId() }.toSet())
        assertEquals(setOf(SYNTHETIC_PINNED_BAR), mirroredControls(classes).getValue(PINNED_BAR).map { it.hookId() }.toSet())
        val method = pb588Fetch()
        val before = method.pbCode()
        hook(method)
        val code = method.pbCode()
        assertEquals(before.size + PB_HOOK_SIZE, code.size)
        assertEquals("$THREAD_KEY_TYPE->A1J()Z", code[3].pbReference())
        assertEquals(PINNED_BAR_CALL, code[5].pbReference())
        assertEquals(Opcode.MOVE_RESULT, code[6].opcode)
        assertEquals(Opcode.IF_EQZ, code[7].opcode)
        // A yes falls into the loader; a no still skips it.
        assertEquals(PB_LOADER, code[8].pbReference())
        assertEquals(Opcode.CONST_4, code[code.branchTarget(7)].opcode)
        // Anything else landing on the loader leaves the gate unproven.
        assertEquals(null, pb588Fetch(extraJump = true).pinnedBarGate(PB_LOADER))
    }

    @Test fun aSecondPassLeavesTheHookAsItIs() {
        val method = pbFetch()
        hook(method)
        val size = method.pbCode().size
        method.validatePinnedBar()
        hook(method)
        assertEquals(size, method.pbCode().size)
    }

    @Test fun onlyTheStockShapeIsTaken() {
        // No loader names the pinned source, or two do.
        assertTrue(findPinnedBar(listOf(fixtureClass(BANNER_FETCH, listOf(pbFetch())), pbLoader(source = "Other"))).isEmpty())
        assertTrue(findPinnedBar(pinnedBarFixture() + pbLoader("LX/4hQ;")).isEmpty())
        // The pinned banner isn't made in the fetch, or isn't behind a thread key check.
        assertTrue(findPinnedBar(listOf(fixtureClass(BANNER_FETCH, listOf(pbFetch(first = "LX/Other;"))), pbLoader())).isEmpty())
        val flagGate = fixtureMethod(SYNTHETIC_PINNED_BAR, """
            invoke-static {v3}, LX/1jx;->A00(Ljava/lang/Object;)Z
            move-result v0
            if-nez v0, :made
            const/4 v0, 0x0
            return-object v0
            :made
            new-instance v0, $PB_LOADER
            return-object v0
        """.trimIndent(), registers = 4)
        assertEquals(null, flagGate.pinnedBarGate(PB_LOADER))
        // A fetch in another class.
        assertTrue(findPinnedBar(listOf(fixtureClass("LX/Other;", listOf(pbFetch())), pbLoader())).isEmpty())
        // Without the loader found with the hooks there's nothing to go on.
        assertFailsWith<PatchException> { pbFetch().validatePinnedBar() }
    }
}
