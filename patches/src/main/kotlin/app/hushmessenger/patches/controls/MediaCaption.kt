package app.hushmessenger.patches.controls

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * #195, #236: where the words you typed land when you send them with a photo or a video. On a plain send Messenger
 * hands the media to its media send first and only then sends the composer's text as a message of its own, so the
 * words come in under the picture whenever the media is ready first. This sends the text first, by the fragment's own
 * text send with the same arguments Messenger gives it, and then raises the flag Messenger keeps for a send whose
 * words already went out with the media: with it up, the rest of the send skips the second text send and clears the
 * composer the way that send does.
 *
 * Messenger only attaches the words to the media itself when the mentions ask for it, like a question to Meta AI
 * about the photo. That send keeps its words with the picture, so its branch is left alone.
 */
internal const val MEDIA_CAPTION = "media_caption"

/** The send the compose fragment marks before it reads the composer. */
private const val SEND_CLICKED = "ComposeFragment.onSendClicked"

private const val CAPTION_MAP = "Lcom/google/common/collect/ImmutableMap;"

private const val CAPTION_STRING = "Ljava/lang/String;"

/** The static helper this control adds to the compose fragment. */
internal const val MEDIA_CAPTION_HELPER = "hushCaptionFirst"

private val CAPTION_STRINGS = setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO)

/**
 * What the helper calls, read from the stock text send: the fragment's text send, the fragment's own reader of the
 * typed text, and the empty map Messenger hands the text send.
 */
internal data class MediaCaptionCalls(val textSend: String, val typedText: String, val emptyMap: String)

/**
 * The plain media send in the composer's send: where it starts, the registers holding the fragment and the flag for
 * words that already went, what the helper calls, and the fragment's media send it calls.
 */
internal class MediaCaptionSite(val at: Int, val fragment: Int, val sent: Int, val calls: MediaCaptionCalls, val mediaSend: String)

/**
 * #236: the second hook of the switch, in the compose fragment's send of a photo or video that comes back from
 * Messenger's editor (Edit on a photo above the message box, or the camera). That send hands the media straight to the
 * fragment's media send and never reads the composer, so the words typed before opening the editor stayed in the box.
 */
internal const val MEDIA_CAPTION_EDITOR = "media_caption_editor"

/** The one string the editor's send loads, for a kind of media it doesn't know. */
private const val EDITOR_SEND_MARK = "Unknown media resource type being sent from Camera"

private const val EDITOR_MESSAGE = "Lcom/facebook/messaging/model/messages/Message;"

/** The static helper the editor's send calls before its media send. */
internal const val MEDIA_CAPTION_EDITOR_HELPER = "hushCaptionForEditor"

/** The kind the editor's send gives a sticker made in the camera, which isn't a photo, so its words stay put. */
internal const val EDITOR_STICKER = "sticker"

/** The editor's media send: where it is, and the registers holding the fragment and the kind of media. */
internal class MediaCaptionEditorSite(val at: Int, val fragment: Int, val kind: Int, val mediaSend: String)

private fun mediaCaptionChanged(detail: String): Nothing =
    throw PatchException("Messenger controls: the composer's send changed ($detail)")

private fun Method.code(): List<Instruction> = implementation?.instructions?.toList().orEmpty()

private fun Instruction.string() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

private fun Instruction.method() = (this as? ReferenceInstruction)?.reference as? MethodReference

private fun MethodReference.shape() = parameterTypes.map { it.toString() }

private fun MethodReference.id() = "$definingClass->$name(${shape().joinToString("")})$returnType"

private fun Instruction.captionRegisters(): List<Int> = when (this) {
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    else -> emptyList()
}

private fun Instruction.captionWrites(register: Int) =
    opcode.setsRegister() && this is OneRegisterInstruction && registerA == register

/** The last instruction in front of [before], after [after], that writes [register]. */
private fun List<Instruction>.lastCaptionWrite(register: Int, after: Int, before: Int): Instruction? =
    (before - 1 downTo after + 1).map { this[it] }.firstOrNull { it.captionWrites(register) }

/** The fragment's own text send: static, returns nothing, takes the fragment, mentions, parameters, text and a list. */
private fun Method.textSends(): List<MethodReference> = code()
    .filter { it.opcode == Opcode.INVOKE_STATIC || it.opcode == Opcode.INVOKE_STATIC_RANGE }
    .mapNotNull { it.method() }
    .filter {
        it.definingClass == definingClass && it.returnType == "V" &&
            it.shape() == listOf(definingClass, IMMUTABLE_LIST, CAPTION_MAP, CAPTION_STRING, "Ljava/util/List;")
    }
    .distinctBy { it.id() }

/**
 * The send's tail, as Messenger 587 and 588 write it:
 *
 *     if-nez vEmpty, :flag            (no media left: straight to the flag)
 *     ...media send(fragment, ..., media list)
 *     :flag
 *     if-eqz vSent, :check            (words still in the composer)
 *     goto :clear
 *     :check
 *     invoke-virtual {fragment}, nothingToSend()Z
 *     move-result vNothing
 *     if-nez vNothing, :done
 *     ...a fresh list, the empty map, null mentions and text
 *     invoke-static {fragment, null, map, null, list}, textSend
 *
 * The hook goes where the media send starts, so the words are on their way before any media is handed over.
 */
internal fun Method.mediaCaptionSite(): MediaCaptionSite? {
    val code = code()
    if (code.none { it.opcode in CAPTION_STRINGS && it.string() == SEND_CLICKED }) return null
    val send = textSends().singleOrNull() ?: return null
    val sendAt = code.indices.singleOrNull { code[it].method()?.id() == send.id() } ?: return null
    val call = code[sendAt] as? FiveRegisterInstruction ?: return null
    if (call.opcode != Opcode.INVOKE_STATIC || call.registerCount != 5) return null
    val fragment = call.registerC
    // In front of it, the question whether there's anything to send as text, skipped past when the answer is no.
    val check = (sendAt - 3 downTo 0).firstOrNull { at ->
        val ask = code[at]
        val asked = ask.method()
        ask.opcode == Opcode.INVOKE_VIRTUAL && asked != null && asked.definingClass == definingClass &&
            asked.parameterTypes.isEmpty() && asked.returnType == "Z" && ask.captionRegisters() == listOf(fragment)
    } ?: return null
    val answer = code[check + 1]
    val skip = code[check + 2]
    if (answer.opcode != Opcode.MOVE_RESULT || skip.opcode != Opcode.IF_NEZ) return null
    if ((answer as OneRegisterInstruction).registerA != (skip as OneRegisterInstruction).registerA) return null
    // From there to the send, only the send's own arguments: no branch, and the stock values for each.
    if ((check + 3 until sendAt).any { code[it] is OffsetInstruction }) return null
    if (call.registerD != call.registerF) return null
    val nothing = code.lastCaptionWrite(call.registerD, check + 2, sendAt)
    if (nothing?.opcode != Opcode.CONST_4 || (nothing as NarrowLiteralInstruction).narrowLiteral != 0) return null
    val map = code.lastCaptionWrite(call.registerE, check + 2, sendAt)
    val mapField = (map as? ReferenceInstruction)?.reference as? FieldReference
    if (map?.opcode != Opcode.SGET_OBJECT || mapField?.type != CAPTION_MAP) return null
    if (code.lastCaptionWrite(call.registerG, check + 2, sendAt)?.opcode != Opcode.MOVE_RESULT_OBJECT) return null
    // The flag: the one branch onto the question, taken while the words are still in the composer, with the jump
    // that clears the composer right after it.
    val flagAt = code.indices.singleOrNull { code[it].opcode == Opcode.IF_EQZ && code.branchTarget(it) == check } ?: return null
    val sent = (code[flagAt] as OneRegisterInstruction).registerA
    if (code.getOrNull(flagAt + 1)?.opcode !in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)) return null
    // Right before the flag, the plain media send: a static send of the fragment's own that takes the fragment and
    // ends with the media list.
    val media = code.getOrNull(flagAt - 1) ?: return null
    val mediaSend = media.method() ?: return null
    if (media.opcode != Opcode.INVOKE_STATIC && media.opcode != Opcode.INVOKE_STATIC_RANGE) return null
    if (mediaSend.definingClass != definingClass || mediaSend.returnType != "V" || mediaSend.shape().lastOrNull() != "Ljava/util/List;") return null
    if (fragment !in media.captionRegisters()) return null
    // And the one branch past it, taken with no media left. The media send's block starts right after that branch.
    val guard = code.indices.singleOrNull { code[it].opcode == Opcode.IF_NEZ && code.branchTarget(it) == flagAt } ?: return null
    val at = guard + 1
    if (at > flagAt - 1) return null
    if ((at until flagAt).any { code[it] is OffsetInstruction || code[it].captionWrites(sent) || code[it].captionWrites(fragment) }) return null
    // The helper's call goes in front of the block's first instruction, so nothing may branch onto it.
    if (at in jumpTargets()) return null
    // The helper's call uses the four-bit form.
    if (fragment > 15 || sent > 15) return null
    // The fragment's own reader of the typed text, which the words-with-the-media branch uses too.
    val reader = code.mapNotNull { insn -> insn.method()?.takeIf { insn.opcode == Opcode.INVOKE_DIRECT } }
        .filter { it.definingClass == definingClass && it.parameterTypes.isEmpty() && it.returnType == CAPTION_STRING }
        .distinctBy { it.id() }.singleOrNull() ?: return null
    return MediaCaptionSite(at, fragment, sent, MediaCaptionCalls(send.id(), reader.id(),
        "${mapField!!.definingClass}->${mapField.name}:${mapField.type}"), mediaSend.id())
}

/**
 * The editor's send, as Messenger 588 writes it:
 *
 *     ...the message's first media, and its kind as a string ("image", "video" or "sticker") in vKind
 *     move-object vFragment, p0
 *     invoke-static/range {...}, mediaSend(..., fragment, ..., kind, media list)
 *     return-void
 *
 * The media send is the same one the composer's send uses for a plain photo. The hook goes right before it, so the
 * words go out only once the media is known and on its way.
 */
internal fun Method.mediaCaptionEditorSite(): MediaCaptionEditorSite? {
    if (AccessFlags.STATIC.isSet(accessFlags) || returnType != "V" || parameterTypes.map { it.toString() } != listOf(EDITOR_MESSAGE)) return null
    val code = code()
    if (code.none { it.opcode in CAPTION_STRINGS && it.string() == EDITOR_SEND_MARK }) return null
    val sends = code.indices.filter { at ->
        val sent = code[at].method()
        (code[at].opcode == Opcode.INVOKE_STATIC || code[at].opcode == Opcode.INVOKE_STATIC_RANGE) && sent != null &&
            sent.definingClass == definingClass && sent.returnType == "V" && sent.shape().lastOrNull() == "Ljava/util/List;"
    }
    val at = sends.singleOrNull() ?: return null
    val send = code[at].method()!!
    val shape = send.shape()
    // One fragment and one kind among the arguments, and nothing wide, so each argument has one register.
    if (shape.count { it == definingClass } != 1 || shape.count { it == CAPTION_STRING } != 1 || shape.any { it == "J" || it == "D" }) return null
    val registers = code[at].captionRegisters()
    if (registers.size != shape.size) return null
    val fragment = registers[shape.indexOf(definingClass)]
    val kind = registers[shape.indexOf(CAPTION_STRING)]
    // The fragment handed over is this one: p0, or a copy of p0 made in the straight run up to the send.
    val self = (implementation?.registerCount ?: return null) - 2
    val targets = jumpTargets()
    if (fragment != self) {
        val copyAt = (at - 1 downTo 0).firstOrNull { code[it].captionWrites(fragment) } ?: return null
        val copy = code[copyAt]
        if (copy.opcode != Opcode.MOVE_OBJECT && copy.opcode != Opcode.MOVE_OBJECT_FROM16) return null
        if ((copy as TwoRegisterInstruction).registerB != self) return null
        if ((copyAt + 1..at).any { it in targets }) return null
    }
    if ((0 until at).any { code[it].captionWrites(self) }) return null
    // The send ends with it.
    if (code.getOrNull(at + 1)?.opcode != Opcode.RETURN_VOID) return null
    // The helper's call goes in front of the media send, so nothing may branch onto it, and it uses the four-bit form.
    if (at in targets || fragment > 15 || kind > 15) return null
    return MediaCaptionEditorSite(at, fragment, kind, send.id())
}

/**
 * The composer view the fragment's reader of the typed text reads first and refuses when it's gone
 * (`iget-object vView, p0, field` and then `if-eqz vView`). An activity result can come back before the view does.
 */
internal fun Method.composerViewField(): String? {
    val code = code()
    val self = (implementation?.registerCount ?: return null) - 1
    if (AccessFlags.STATIC.isSet(accessFlags) || parameterTypes.isNotEmpty()) return null
    val read = code.firstOrNull() ?: return null
    val field = (read as? ReferenceInstruction)?.reference as? FieldReference ?: return null
    if (read.opcode != Opcode.IGET_OBJECT || (read as TwoRegisterInstruction).registerB != self || field.definingClass != definingClass) return null
    val view = (read as OneRegisterInstruction).registerA
    val check = (1 until minOf(code.size, 4)).firstOrNull { code[it].opcode == Opcode.IF_EQZ && (code[it] as OneRegisterInstruction).registerA == view }
        ?: return null
    if ((1 until check).any { code[it].captionWrites(view) || code[it] is OffsetInstruction }) return null
    return "${field.definingClass}->${field.name}:${field.type}"
}

/**
 * The editor's send in each compose fragment whose composer send [findMediaCaption] found, calling the same media
 * send, in a fragment whose reader of the typed text checks for the composer view first.
 */
internal fun findMediaCaptionEditor(classes: Iterable<ClassDef>, sends: List<Method>): List<Method> {
    val sites = sends.mapNotNull { send -> send.mediaCaptionSite()?.let { send.definingClass to it } }.toMap()
    return classes.filter { it.type in sites }.flatMap { host ->
        val site = sites.getValue(host.type)
        if (host.methods.singleOrNull { it.hookId() == site.calls.typedText }?.composerViewField() == null) return@flatMap emptyList<Method>()
        host.methods.filter { it.mediaCaptionEditorSite()?.mediaSend == site.mediaSend }
    }
}

internal fun MutableMethod.validateMediaCaptionEditor(): MediaCaptionEditorSite =
    mediaCaptionEditorSite() ?: mediaCaptionChanged("no media send at the end of the editor's send in ${hookId()}")

/** Right before the editor's media send, the helper gets the fragment and the kind of media. */
internal fun MutableMethod.injectMediaCaptionEditor() {
    val site = validateMediaCaptionEditor()
    addInstructions(site.at,
        "invoke-static {v${site.fragment}, v${site.kind}}, $definingClass->$MEDIA_CAPTION_EDITOR_HELPER($definingClass$CAPTION_STRING)V")
}

/**
 * The static helper the editor's send calls with the fragment and the kind of media. With the composer view there and
 * anything but a sticker, it hands the fragment to [mediaCaptionHelper] with the flag down, which sends the words when
 * the switch is on and something is typed. Messenger's text send clears the box as it always does.
 */
internal fun mediaCaptionEditorHelper(host: String, composerView: String): MutableMethod {
    val parameters = listOf(host, CAPTION_STRING).map { ImmutableMethodParameter(it, null, null) }
    // p0 is the fragment, p1 the kind of media.
    return MutableMethod(ImmutableMethod(host, MEDIA_CAPTION_EDITOR_HELPER, parameters, "V",
        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, null, null, ImmutableMethodImplementation(3, emptyList(), null, null)))
        .apply {
            addInstructionsWithLabels(0, """
                iget-object v0, p0, $composerView
                if-eqz v0, :done
                const-string v0, "$EDITOR_STICKER"
                invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v0
                if-nez v0, :done
                const/4 v0, 0x0
                invoke-static {p0, v0}, $host->$MEDIA_CAPTION_HELPER(${host}I)I
                :done
                return-void
            """.trimIndent())
        }
}

/**
 * The one send whose words go out after its media. A build whose send no longer reads like that gets no hook, and the
 * switch shows as unavailable instead of moving a message somewhere it doesn't belong.
 */
internal fun findMediaCaption(classes: Iterable<ClassDef>): List<Method> =
    classes.flatMap { it.methods }.filter { it.mediaCaptionSite() != null }

internal fun MutableMethod.validateMediaCaption(): MediaCaptionSite =
    mediaCaptionSite() ?: mediaCaptionChanged("no plain media send ahead of the text send in ${hookId()}")

/**
 * Right before the media send, the helper gets the fragment and the flag, sends the words when the switch is on, and
 * hands the flag back raised. Off, Pause and safe mode, nothing typed, or a send whose words already went with the
 * media get the flag back as it was.
 */
internal fun MutableMethod.injectMediaCaption() {
    val site = validateMediaCaption()
    addInstructions(site.at, """
        invoke-static {v${site.fragment}, v${site.sent}}, $definingClass->$MEDIA_CAPTION_HELPER(${definingClass}I)I
        move-result v${site.sent}
    """.trimIndent())
}

/**
 * The static helper the composer's send calls with the fragment and the flag for words that already went. With the
 * switch on and something typed, it sends the text the way the stock text send does, with no mentions or text handed
 * in so the send reads both from the composer, and answers 1. Otherwise it answers with the flag it was given.
 */
internal fun mediaCaptionHelper(host: String, calls: MediaCaptionCalls): MutableMethod {
    val parameters = listOf(host, "I").map { ImmutableMethodParameter(it, null, null) }
    // p0 is the fragment, p1 the flag.
    return MutableMethod(ImmutableMethod(host, MEDIA_CAPTION_HELPER, parameters, "I",
        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, null, null, ImmutableMethodImplementation(5, emptyList(), null, null)))
        .apply {
            addInstructionsWithLabels(0, """
                if-nez p1, :as_is
                invoke-direct {p0}, ${calls.typedText}
                move-result-object v0
                invoke-static {v0}, $SETTINGS->captionBeforeMedia($CAPTION_STRING)Z
                move-result v0
                if-eqz v0, :as_is
                const/4 v0, 0x0
                sget-object v1, ${calls.emptyMap}
                new-instance v2, Ljava/util/ArrayList;
                invoke-direct {v2}, Ljava/util/ArrayList;-><init>()V
                invoke-static {p0, v0, v1, v0, v2}, ${calls.textSend}
                const/4 p1, 0x1
                :as_is
                return p1
            """.trimIndent())
        }
}

/** Whether the compose fragment can take [mediaCaptionHelper], or the helper named [name]. */
internal fun ClassDef.checkMediaCaptionHelper(name: String = MEDIA_CAPTION_HELPER) {
    if (methods.any { it.name == name })
        throw PatchException("Messenger controls: $type already has $name")
}

/** Adds [mediaCaptionHelper] or [mediaCaptionEditorHelper] to the compose fragment once [checkMediaCaptionHelper] passed. */
internal fun MutableClass.addMediaCaptionHelper(helper: MutableMethod) {
    checkMediaCaptionHelper(helper.name)
    methods.add(helper)
    directMethods.add(helper)
}
