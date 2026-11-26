package app.hushmessenger.patches.controls

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * #213: Messenger's own pinned message bar under a chat's name. Two hooks: the loader's gate here, and the slot's ranking
 * below, since a chat shows one banner at a time.
 *
 * A chat's banners are gathered in ThreadViewBannerDataFetch, which adds the pinned messages loader only for some chats:
 * the ones whose thread key answers yes to one check, and otherwise only behind a server flag most accounts don't get.
 * The loader is the class whose static initializer names PinnedMessagesMsysBannerDataSource. The thread key check's
 * answer goes through the extension first, which says yes while Show pinned messages is on, so every chat takes the
 * path those chats take and Messenger's bar shows the chat's pinned messages.
 *
 * The check is the ThreadKey ()Z call whose move-result feeds the if-nez that lands on the loader's new-instance, or in
 * 588 the if-eqz right above that new-instance, which skips the loader on a no. Other
 * banners sit behind gates of the same shape, so the loader found with the hooks picks which gate is the pinned one. The
 * fetch class keeps its own name in every build.
 */
internal const val PINNED_BAR = "pinned_bar"
internal const val PINNED_BAR_CALL = "Lapp/hushmessenger/extension/PinnedBar;->show(Z)Z"
internal const val PINNED_BAR_SOURCE = "PinnedMessagesMsysBannerDataSource"
internal const val BANNER_FETCH = "Lcom/facebook/messaging/msys/thread/banner/datafetch/ThreadViewBannerDataFetch;"

/** The pinned messages loader of the build being patched, found with the hooks. */
internal var pinnedBarLoader: String? = null

/** Where the thread key's answer lands: the move-result's index and register, and whether the hook is in already. */
internal class PinnedGate(val index: Int, val register: Int, val hooked: Boolean)

private fun Instruction.isThreadKeyCheck(): Boolean {
    val call = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return opcode == Opcode.INVOKE_VIRTUAL && call.definingClass == THREAD_KEY_TYPE && call.returnType == "Z" && call.parameterTypes.isEmpty()
}

/** The gate in front of [loader]'s new-instance, or null when [this] has no such gate. */
internal fun Method.pinnedBarGate(loader: String): PinnedGate? {
    val code = implementation?.instructions?.toList() ?: return null
    val made = code.indices.singleOrNull {
        code[it].opcode == Opcode.NEW_INSTANCE && (code[it] as ReferenceInstruction).reference.toString() == loader
    } ?: return null
    // 587 jumps onto the loader with an if-nez. 588 falls into it from an if-eqz right above that skips it, and nothing
    // else may land on the loader then.
    val jumps = code.indices.filter { code[it].opcode == Opcode.IF_NEZ && code.branchTarget(it) == made }
    val branch = when {
        jumps.size == 1 -> jumps.single()
        jumps.isEmpty() && made >= 1 && code[made - 1].opcode == Opcode.IF_EQZ && code.branchTarget(made - 1) > made &&
            made !in jumpTargets() -> made - 1
        else -> return null
    }
    if (branch < 2 || branch in jumpTargets()) return null
    val register = (code[branch] as OneRegisterInstruction).registerA
    val result = code[branch - 1]
    if (result.opcode != Opcode.MOVE_RESULT || (result as OneRegisterInstruction).registerA != register || register > 0xff) return null
    val read = code[branch - 2]
    if (read.opcode == Opcode.INVOKE_STATIC_RANGE && (read as ReferenceInstruction).reference.toString() == PINNED_BAR_CALL) {
        // Hooked already: the key's own call and its move-result sit right above.
        if (branch < 4 || code[branch - 3].opcode != Opcode.MOVE_RESULT || !code[branch - 4].isThreadKeyCheck()) return null
        return PinnedGate(branch - 3, register, true)
    }
    if (!read.isThreadKeyCheck()) return null
    return PinnedGate(branch - 1, register, false)
}

/** The pinned messages loader, by the data source it names. */
internal val pinnedBarLoaders = ClassCheck(loadsString(PINNED_BAR_SOURCE))

internal fun resolvePinnedBarLoader(classes: Iterable<ClassDef>): String? = pinnedBarLoaders.classes(classes).singleOrNull()?.type

/** The banner fetch method, taken only while exactly one loader class names the source and one method gates it. */
internal fun findPinnedBar(classes: Iterable<ClassDef>, loader: String? = resolvePinnedBarLoader(classes)): List<Method> {
    loader ?: return emptyList()
    val fetch = classes.firstOrNull { it.type == BANNER_FETCH } ?: return emptyList()
    return listOfNotNull(fetch.methods.singleOrNull { it.pinnedBarGate(loader) != null })
}

private fun pinnedChanged(method: Method): Nothing =
    throw PatchException("Messenger controls: the chat banner fetch ${method.hookId()} no longer matches the tested build")

internal fun Method.validatePinnedBar(): PinnedGate = pinnedBarGate(pinnedBarLoader ?: pinnedChanged(this)) ?: pinnedChanged(this)

/** The thread key's answer goes through the extension, which says yes while the switch is on and passes it along otherwise. */
internal fun MutableMethod.injectPinnedBar() {
    val gate = validatePinnedBar()
    if (gate.hooked) return
    addInstructions(gate.index + 1, """
        invoke-static/range {v${gate.register} .. v${gate.register}}, $PINNED_BAR_CALL
        move-result v${gate.register}
    """.trimIndent())
}

/*
 * #213, the slot. A chat shows one banner under its name at a time: of the banners its loaders turn up, the one Messenger's
 * DefaultBannerPrioritizer ranks lowest wins. The pinned messages bar ranks 19 there and the reminder about deleted
 * messages 1, so a one-to-one chat with that reminder never showed the bar. The rank of a pinned messages banner goes
 * through the extension, which puts it ahead of every other banner while Show pinned messages is on.
 *
 * The prioritizer is the one method that logs "Missing priority for banner: " under its own name. It looks the banner's
 * class up in its map and returns the Integer's intValue, and the hook sits between that move-result and the return,
 * where nothing jumps. It reads the banner from the parameter's register, which nothing in the method writes, so it's
 * still the banner the method was called with. The pinned banner is the class Messenger builds for an open banner of
 * type 0x3dc, the one the pinned loader keeps in a field.
 */
internal const val PINNED_BAR_FIRST = "pinned_bar_first"
internal const val PINNED_BAR_RANK_CALL = "Lapp/hushmessenger/extension/PinnedBar;->bannerRank(II)I"
internal const val BANNER_RANK_MISSING = "Missing priority for banner: "
internal const val BANNER_PRIORITIZER = "DefaultBannerPrioritizer"
private const val BANNER_RANK_READ = "Ljava/lang/Number;->intValue()I"

/** The open banner type msys gives a chat's pinned messages. */
internal const val PINNED_BANNER_TYPE = 0x3dc

/** The pinned messages banner of the build being patched, found with the hooks. */
internal var pinnedBarModel: String? = null

/** The prioritizer's return: where the hook goes in, the banner's and the rank's registers, and whether it's in already. */
internal class BannerRankSite(val index: Int, val banner: Int, val rank: Int, val hooked: Boolean)

/** The banner prioritizer, by the two strings it logs a banner with no rank under. */
internal val bannerPrioritizers = MethodCheck {
    val missing = StringMatch(BANNER_RANK_MISSING)
    val owner = StringMatch(BANNER_PRIORITIZER)
    val test: MethodTest = { _, method -> missing.inCode(method) && owner.inCode(method) }
    test
}

private fun Instruction.bannerRankWrites(register: Int): Boolean {
    if (!opcode.setsRegister() || this !is OneRegisterInstruction) return false
    return registerA == register || (opcode.setsWideRegister() && registerA + 1 == register)
}

/** Where [this] returns a banner's rank, or null when it isn't the stock prioritizer's shape with [model] as the pinned banner. */
internal fun Method.bannerRankSite(model: String): BannerRankSite? {
    if (AccessFlags.STATIC.isSet(accessFlags) || returnType != "I" || parameterTypes.size != 1) return null
    if (!parameterTypes.single().toString().startsWith("L")) return null
    val body = implementation ?: return null
    val code = body.instructions.toList()
    // One reference parameter after this: its register is the frame's last.
    val banner = body.registerCount - 1
    val read = code.indices.singleOrNull {
        code[it].opcode == Opcode.INVOKE_VIRTUAL && (code[it] as ReferenceInstruction).reference.toString() == BANNER_RANK_READ
    } ?: return null
    val result = code.getOrNull(read + 1) ?: return null
    if (result.opcode != Opcode.MOVE_RESULT) return null
    val rank = (result as OneRegisterInstruction).registerA
    if (banner > 15 || rank > 15 || banner == rank) return null
    val hooked = code.getOrNull(read + 2)?.opcode == Opcode.INSTANCE_OF
    if (hooked) {
        val test = code[read + 2] as TwoRegisterInstruction
        if (test.registerA != banner || test.registerB != banner || (test as ReferenceInstruction).reference.toString() != model) return null
        val call = code.getOrNull(read + 3) as? FiveRegisterInstruction ?: return null
        if (call.opcode != Opcode.INVOKE_STATIC || (call as ReferenceInstruction).reference.toString() != PINNED_BAR_RANK_CALL) return null
        if (call.registerCount != 2 || call.registerC != banner || call.registerD != rank) return null
        val answer = code.getOrNull(read + 4) ?: return null
        if (answer.opcode != Opcode.MOVE_RESULT || (answer as OneRegisterInstruction).registerA != rank) return null
    }
    val back = if (hooked) read + 5 else read + 2
    val done = code.getOrNull(back) ?: return null
    if (done.opcode != Opcode.RETURN || (done as OneRegisterInstruction).registerA != rank) return null
    val targets = jumpTargets()
    if (read + 1 in targets || read + 2 in targets || back in targets) return null
    // Nothing but the hook's own test writes the banner's register, so the hook reads the banner the method was handed.
    if (code.indices.any { (!hooked || it != read + 2) && code[it].bannerRankWrites(banner) }) return null
    return BannerRankSite(read + 2, banner, rank, hooked)
}

/** The types [this] builds right where it branches on an open banner of type 0x3dc. */
private fun Method.pinnedBannersBuilt(into: MutableSet<String>) {
    val code = implementation?.instructions?.toList() ?: return
    for (i in 0 until code.size - 1) {
        val load = code[i]
        if (load.opcode != Opcode.CONST_16 && load.opcode != Opcode.CONST) continue
        if ((load as NarrowLiteralInstruction).narrowLiteral != PINNED_BANNER_TYPE) continue
        val branch = code[i + 1]
        val register = (load as OneRegisterInstruction).registerA
        if (branch.opcode != Opcode.IF_EQ || ((branch as TwoRegisterInstruction).registerA != register && branch.registerB != register)) continue
        val target = code.branchTarget(i + 1)
        val made = code.getOrNull(target) ?: continue
        val init = code.getOrNull(target + 1) ?: continue
        if (made.opcode != Opcode.NEW_INSTANCE) continue
        val type = (made as ReferenceInstruction).reference.toString()
        val call = (init as? ReferenceInstruction)?.reference as? MethodReference ?: continue
        if ((init.opcode == Opcode.INVOKE_DIRECT || init.opcode == Opcode.INVOKE_DIRECT_RANGE) && call.definingClass == type && call.name == "<init>")
            into += type
    }
}

/** The pinned messages banner: the one class built for type 0x3dc that the pinned loader keeps in a field of its own. */
internal fun resolvePinnedBarModel(classes: Iterable<ClassDef>, loader: String? = resolvePinnedBarLoader(classes)): String? {
    loader ?: return null
    val held = classes.firstOrNull { it.type == loader }?.instanceFields?.map { it.type }?.toSet() ?: return null
    val defined = classes.filter { it.type in held }.map { it.type }
    if (defined.isEmpty()) return null
    val built = mutableSetOf<String>()
    for ((_, method) in codeReferrers(classes, types = defined)) method.pinnedBannersBuilt(built)
    return built.filter { it in defined }.singleOrNull()
}

/** The prioritizer, taken only while exactly one method logs under its name and it returns the rank in the stock shape. */
internal fun findPinnedBarFirst(classes: Iterable<ClassDef>, model: String? = resolvePinnedBarModel(classes)): List<Method> {
    model ?: return emptyList()
    val prioritizer = bannerPrioritizers.methods(classes).singleOrNull() ?: return emptyList()
    return listOfNotNull(prioritizer.takeIf { it.bannerRankSite(model) != null })
}

private fun rankChanged(method: Method): Nothing =
    throw PatchException("Messenger controls: the chat banner prioritizer ${method.hookId()} no longer matches the tested build")

internal fun Method.validatePinnedBarFirst(): BannerRankSite = bannerRankSite(pinnedBarModel ?: rankChanged(this)) ?: rankChanged(this)

/** A pinned messages banner's rank goes through the extension, which puts it first while the switch is on. */
internal fun MutableMethod.injectPinnedBarFirst() {
    val site = validatePinnedBarFirst()
    if (site.hooked) return
    val model = pinnedBarModel ?: rankChanged(this)
    addInstructions(site.index, """
        instance-of v${site.banner}, v${site.banner}, $model
        invoke-static {v${site.banner}, v${site.rank}}, $PINNED_BAR_RANK_CALL
        move-result v${site.rank}
    """.trimIndent())
}
