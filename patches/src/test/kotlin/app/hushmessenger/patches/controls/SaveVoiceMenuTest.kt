package app.hushmessenger.patches.controls

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PARAMS_BUILDER = "LX/Rpb;"
private const val ITEM_BUILDER = "LX/MFj;"
private const val TAP_OWNER = "LX/9qR;"
private const val HANDLER = "LX/7He;"
private const val SAVE_ROW_OWNER = "LX/Muc;"
private const val AUDIO_FETCHER = "LX/W81;"
private const val AUDIO_REQUEST = "LX/W85;"
private const val PREFETCHER = "LX/W8a;"
private const val HOLDER = "LX/2zt;"
private const val READER = "LX/4cV;"
private const val CLASSIFIER = "LX/4cW;"
private const val KIND = "LX/50e;"
private const val OBJECT = "Ljava/lang/Object;"
private const val URI = "Landroid/net/Uri;"
private const val COPY_OF = "Lcom/google/common/collect/ImmutableList;->copyOf(Ljava/util/Collection;)Lcom/google/common/collect/ImmutableList;"
private const val ICON = 0x7f1d011d
private val PUBLIC = AccessFlags.PUBLIC.value
private val PUBLIC_STATIC = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value

/** The synthetic labels for the two hooks, named after the first build they were found in. */
internal const val MENU_ROWS_CTOR = "$MENU_PARAMS-><init>($PARAMS_BUILDER)V"
internal const val MENU_TAP = "$TAP_OWNER->E0b($MENU_ITEM$OBJECT)Z"

private val ROWS_BODY = """
    invoke-direct {v5}, $OBJECT-><init>()V
    iget-object v0, v6, $PARAMS_BUILDER->A03:Ljava/util/List;
    invoke-static {v0}, $COPY_OF
    move-result-object v3
    iput-object v3, v5, $MENU_PARAMS->A01:Lcom/google/common/collect/ImmutableList;
    iget-object v0, v6, $PARAMS_BUILDER->A01:$OBJECT
    iput-object v0, v5, $MENU_PARAMS->A02:$OBJECT
    return-void
""".trimIndent()

private val TAP_BODY = """
    move-object/from16 v3, v20
    move-object/from16 v4, v22
    iget v0, v3, $TAP_OWNER->A01:I
    move-object/from16 v11, v21
    const/4 v1, 0x0
    if-eqz v0, :other
    const-string v0, "$MENU_TAP_NAME"
    iget-object v3, v3, $TAP_OWNER->A00:$OBJECT
    check-cast v3, $HANDLER
    const-string v0, "$MENU_ROW_MESSAGE"
    const/4 v0, 0x1
    return v0
    :other
    const/4 v0, 0x0
    return v0
""".trimIndent()

/** Reads the builder the way MenuDialogItem does: title resource, id, icon, then two more ints. */
private val ITEM_BODY = """
    invoke-direct {v4}, $OBJECT-><init>()V
    iget v3, v5, $ITEM_BUILDER->A04:I
    iget-object v1, v5, $ITEM_BUILDER->A06:Ljava/lang/CharSequence;
    iget v0, v5, $ITEM_BUILDER->A03:I
    iput v0, v4, $MENU_ITEM->A03:I
    iput v3, v4, $MENU_ITEM->A04:I
    iget v0, v5, $ITEM_BUILDER->A02:I
    iput v0, v4, $MENU_ITEM->A02:I
    iget v0, v5, $ITEM_BUILDER->A01:I
    iput v0, v4, $MENU_ITEM->A01:I
    iget v0, v5, $ITEM_BUILDER->A00:I
    iput v0, v4, $MENU_ITEM->A00:I
    iput-object v1, v4, $MENU_ITEM->A07:Ljava/lang/CharSequence;
    iget-object v0, v5, $ITEM_BUILDER->A07:Ljava/lang/String;
    iput-object v0, v4, $MENU_ITEM->A08:Ljava/lang/String;
    return-void
""".trimIndent()

private val SAVE_ROW_BODY = """
    new-instance v1, $ITEM_BUILDER
    invoke-direct {v1}, $OBJECT-><init>()V
    const v0, 0x7f142ad3
    iput v0, v1, $ITEM_BUILDER->A04:I
    const v0, $ICON
    iput v0, v1, $ITEM_BUILDER->A02:I
    const-string v0, "$MENU_SAVE_VIDEO"
    iput-object v0, v1, $ITEM_BUILDER->A07:Ljava/lang/String;
    new-instance v0, $MENU_ITEM
    invoke-direct {v0, v1}, $MENU_ITEM-><init>($ITEM_BUILDER)V
    return-object v0
""".trimIndent()

private val READ_AUDIO = "$READER->A0A($FB_USER_SESSION${MESSAGE}Ljava/lang/String;)$MENU_AUDIO_DATA"
private val CLASSIFY = "$CLASSIFIER->A00($CLASSIFIER$MESSAGE)$KIND"
private val ASK = "$CLASSIFIER->A01($FB_USER_SESSION$MESSAGE)$KIND"

private val PREFETCH_BODY = """
    iget-object v0, v2, $PREFETCHER->A00:$HOLDER
    invoke-static {v0}, $HOLDER->A0E($HOLDER)$OBJECT
    move-result-object v1
    check-cast v1, $READER
    const/4 v0, 0x0
    invoke-virtual {v1, v3, v4, v0}, $READ_AUDIO
    move-result-object v0
    if-eqz v0, :none
    iget-object v0, v0, $MENU_AUDIO_DATA->A01:$URI
    :none
    return-void
""".trimIndent()

private val READ_BODY = """
    iget-object v0, v2, $READER->A08:$CLASSIFIER
    invoke-virtual {v0, v3, v4}, $ASK
    move-result-object v1
    sget-object v0, $KIND->A03:$KIND
    if-ne v1, v0, :other
    :other
    const/4 v0, 0x0
    return-object v0
""".trimIndent()

private fun field(owner: String, name: String, type: String, flags: Int = PUBLIC) = ImmutableField(owner, name, type, flags, null, null, null)
private fun constructor(owner: String, parameters: String = "", registers: Int = 1 + parameters.length.coerceAtMost(1)) =
    fixtureMethod("$owner-><init>($parameters)V", "return-void", registers)

private fun rowsMethod(body: String = ROWS_BODY) = fixtureMethod(MENU_ROWS_CTOR, body, 7)
private fun tapMethod(id: String = MENU_TAP, body: String = TAP_BODY, flags: Int = PUBLIC) = fixtureMethod(id, body, 23, flags)
private fun tapClass(method: Method = tapMethod(), fieldFlags: Int = PUBLIC, flags: Int = PUBLIC) =
    fixtureClass(TAP_OWNER, listOf(method), extraFields = listOf(field(TAP_OWNER, "A00", OBJECT, fieldFlags), field(TAP_OWNER, "A01", "I")), flags = flags)
private fun handlerClass(sessionFlags: Int = PUBLIC, flags: Int = PUBLIC, sessions: Int = 1) = fixtureClass(HANDLER, extraFields = (0 until sessions).map {
    field(HANDLER, "A0h$it", FB_USER_SESSION, sessionFlags)
}, flags = flags)
private fun itemClass(body: String = ITEM_BODY) = fixtureClass(MENU_ITEM, listOf(fixtureMethod("$MENU_ITEM-><init>($ITEM_BUILDER)V", body, 6),
    constructor(MENU_ITEM, "Landroid/os/Parcel;", 2)), extraFields = listOf(field(MENU_ITEM, "A03", "I")))
private fun itemBuilderClass(idFlags: Int = PUBLIC, withConstructor: Boolean = true) = fixtureClass(ITEM_BUILDER,
    if (withConstructor) listOf(constructor(ITEM_BUILDER)) else emptyList(), extraFields = listOf(
        field(ITEM_BUILDER, "A00", "I"), field(ITEM_BUILDER, "A01", "I"), field(ITEM_BUILDER, "A02", "I"), field(ITEM_BUILDER, "A03", "I", idFlags),
        field(ITEM_BUILDER, "A04", "I"), field(ITEM_BUILDER, "A06", "Ljava/lang/CharSequence;"), field(ITEM_BUILDER, "A07", "Ljava/lang/String;"),
    ))
private fun saveRowClass(body: String = SAVE_ROW_BODY) = fixtureClass(SAVE_ROW_OWNER, listOf(fixtureMethod("$SAVE_ROW_OWNER->AWT()$MENU_ITEM", body, 3)))
private fun fetcherClass(withConstructor: Boolean = true, fieldFlags: Int = PUBLIC) = fixtureClass(AUDIO_FETCHER,
    saveVoiceFixture().single().methods.toList() + if (withConstructor) listOf(constructor(AUDIO_FETCHER)) else emptyList(),
    extraFields = listOf(field(AUDIO_FETCHER, "A0B", PREFETCHER, fieldFlags)))
private fun requestClass(parameters: String = "Z$URI") = fixtureClass(AUDIO_REQUEST, listOf(constructor(AUDIO_REQUEST, parameters, 3)))
private fun prefetcherClass(body: String = PREFETCH_BODY) = fixtureClass(PREFETCHER,
    listOf(fixtureMethod("$PREFETCHER->A01($FB_USER_SESSION$MESSAGE)V", body, 5)), extraFields = listOf(field(PREFETCHER, "A00", HOLDER)))
private fun holderClass(flags: Int = PUBLIC_STATIC) = fixtureClass(HOLDER, listOf(fixtureMethod("$HOLDER->A0E($HOLDER)$OBJECT", "const/4 v0, 0x0\nreturn-object v0", 2, flags)))
private fun readerClass(body: String = READ_BODY) = fixtureClass(READER, listOf(fixtureMethod(READ_AUDIO, body, 6)), extraFields = listOf(field(READER, "A08", CLASSIFIER)))
private fun classifierClass(asks: String = "invoke-static {v1, v3}, $CLASSIFY\nmove-result-object v0\nreturn-object v0", withStep: Boolean = true) = fixtureClass(CLASSIFIER,
    (if (withStep) listOf(fixtureMethod(CLASSIFY, "const/4 v0, 0x0\nreturn-object v0", 3, PUBLIC_STATIC)) else emptyList()) + fixtureMethod(ASK, asks, 4))
private fun kindClass(fieldType: String = KIND) = fixtureClass(KIND, extraFields = listOf(field(KIND, "A03", fieldType, PUBLIC_STATIC)))
private fun audioDataClass(uriFlags: Int = PUBLIC) = fixtureClass(MENU_AUDIO_DATA, extraFields = listOf(field(MENU_AUDIO_DATA, "A01", URI, uriFlags)))

/** Every piece the row needs, each cut down to what every build shares. A piece passed in takes the place of the one of its type. */
internal fun saveVoiceMenuFixture(vararg changed: MutableClass): List<MutableClass> {
    val swapped = changed.associateBy { it.type }
    return listOf(
        fixtureClass(MENU_PARAMS, listOf(rowsMethod())), tapClass(), handlerClass(), itemClass(), itemBuilderClass(), saveRowClass(), fetcherClass(), requestClass(),
        prefetcherClass(), holderClass(), readerClass(), classifierClass(), kindClass(), audioDataClass(), fixtureClass(MESSAGE),
    ).map { swapped[it.type] ?: it }
}

internal fun saveVoiceMenuStub(id: String): MutableMethod {
    val words = if (id == SAVE_VOICE_MENU_ROW || id == SAVE_VOICE_MENU_FETCH) 3 else 2
    val body = if (id == SAVE_VOICE_MENU_ROW_ID || id == SAVE_VOICE_MENU_IS_VOICE) "const/4 v0, 0x0\nreturn v0" else "const/4 v0, 0x0\nreturn-object v0"
    return fixtureMethod(id, body, words, AccessFlags.STATIC.value)
}

private fun Method.code(): List<Instruction> = implementation!!.instructions.toList()
private fun Instruction.reference() = (this as? ReferenceInstruction)?.reference?.toString()
private fun Instruction.arguments() = (this as FiveRegisterInstruction).let { listOf(it.registerC, it.registerD, it.registerE, it.registerF, it.registerG).take(it.registerCount) }

class SaveVoiceMenuTest {
    @AfterTest fun reset() {
        activeProfile = SYNTHETIC_PROFILE
        saveVoiceMenuRecipe = null
    }

    private fun found(classes: List<MutableClass>) = mirroredControls(classes).getValue(SAVE_VOICE_MENU).map { it.hookId() }.toSet()

    @Test fun theMenuAndItsTapAreFoundByTheirShape() {
        assertEquals(setOf(MENU_ROWS_CTOR, MENU_TAP), found(saveVoiceMenuFixture()))
        validateControls(mirroredControls(saveVoiceMenuFixture()), setOf(SAVE_VOICE_MENU))
    }

    @Test fun everyPieceComesFromMessengersOwnCode() {
        val recipe = assertNotNull(resolveSaveVoiceMenu(saveVoiceMenuFixture()))
        assertEquals(ITEM_BUILDER, recipe.builder)
        assertEquals("$ITEM_BUILDER->A03:I", recipe.builderId)
        assertEquals("$ITEM_BUILDER->A06:Ljava/lang/CharSequence;", recipe.builderTitle)
        assertEquals("$ITEM_BUILDER->A02:I", recipe.builderIcon)
        assertEquals("$ITEM_BUILDER->A07:Ljava/lang/String;", recipe.builderTag)
        assertEquals("$MENU_ITEM->A03:I", recipe.itemId)
        assertEquals(ICON, recipe.icon)
        assertEquals("$TAP_OWNER->A00:$OBJECT", recipe.tapHandler)
        assertEquals(HANDLER, recipe.handler)
        assertEquals("$HANDLER->A0h0:$FB_USER_SESSION", recipe.session)
        assertEquals(AUDIO_FETCHER, recipe.fetcher)
        assertEquals("$AUDIO_FETCHER->A0B:$PREFETCHER", recipe.prefetcher)
        assertEquals("$PREFETCHER->A00:$HOLDER", recipe.holder)
        assertEquals("$HOLDER->A0E($HOLDER)$OBJECT", recipe.holderGet)
        assertEquals(READER, recipe.reader)
        assertEquals("$READER->A08:$CLASSIFIER", recipe.classifier)
        assertEquals(CLASSIFY, recipe.classify)
        assertEquals("$KIND->A03:$KIND", recipe.audioKind)
        assertEquals(READ_AUDIO, recipe.readAudio)
        assertEquals("$MENU_AUDIO_DATA->A01:$URI", recipe.audioUri)
        assertEquals(AUDIO_REQUEST, recipe.request)
        assertEquals(VOICE_DOWNLOAD, recipe.download)
    }

    @Test fun lookalikesAndPiecesItCantReachAreNotTheMenu() {
        val privateField = AccessFlags.PRIVATE.value
        val finalField = PUBLIC or AccessFlags.FINAL.value
        val cases = mapOf(
            "another list copied" to saveVoiceMenuFixture(fixtureClass(MENU_PARAMS, listOf(rowsMethod(ROWS_BODY.replace("A03:Ljava/util/List;", "A03:Ljava/util/Collection;"))))),
            "the copy's result in the list's register" to saveVoiceMenuFixture(fixtureClass(MENU_PARAMS, listOf(rowsMethod(ROWS_BODY.replace("move-result-object v3", "move-result-object v0"))))),
            "no extra on the builder" to saveVoiceMenuFixture(fixtureClass(MENU_PARAMS, listOf(rowsMethod(ROWS_BODY.replace("A01:$OBJECT", "A01:Ljava/lang/String;"))))),
            "a static tap" to saveVoiceMenuFixture(tapClass(tapMethod(flags = PUBLIC_STATIC))),
            "a tap that reads no message" to saveVoiceMenuFixture(tapClass(tapMethod(body = TAP_BODY.replace(MENU_ROW_MESSAGE, "row")))),
            "a tap that overwrites its item first" to saveVoiceMenuFixture(tapClass(tapMethod(body = "const/16 v21, 0x0\n$TAP_BODY"))),
            "a tap with no free register" to saveVoiceMenuFixture(tapClass(tapMethod().starved())),
            "a tap that keeps its handler private" to saveVoiceMenuFixture(tapClass(fieldFlags = privateField)),
            "a tap in a private class" to saveVoiceMenuFixture(tapClass(flags = 0)),
            "a private account" to saveVoiceMenuFixture(handlerClass(sessionFlags = privateField)),
            "two accounts" to saveVoiceMenuFixture(handlerClass(sessions = 2)),
            "a private handler" to saveVoiceMenuFixture(handlerClass(flags = 0)),
            "an item that reads four ints" to saveVoiceMenuFixture(itemClass(ITEM_BODY.replace("    iget v0, v5, $ITEM_BUILDER->A00:I\n", "").replace("iget v0, v5, $ITEM_BUILDER->A00:I\n", ""))),
            "an item that keeps no id" to saveVoiceMenuFixture(itemClass(ITEM_BODY.replace("iput v0, v4, $MENU_ITEM->A03:I", "iput v0, v4, $MENU_ITEM->A03:Z"))),
            "an item builder with no constructor" to saveVoiceMenuFixture(itemBuilderClass(withConstructor = false)),
            "an id that can't be set" to saveVoiceMenuFixture(itemBuilderClass(idFlags = finalField)),
            "no Save video row" to saveVoiceMenuFixture().filter { it.type != SAVE_ROW_OWNER },
            "an icon that isn't a constant" to saveVoiceMenuFixture(saveRowClass(SAVE_ROW_BODY.replace("const v0, $ICON", "const/4 v2, 0x0\nmove v0, v2"))),
            "a fetcher with no constructor" to saveVoiceMenuFixture(fetcherClass(withConstructor = false)),
            "a private prefetcher" to saveVoiceMenuFixture(fetcherClass(fieldFlags = privateField)),
            "a request with no constructor" to saveVoiceMenuFixture(requestClass("I$URI")),
            "a prefetch in another order" to saveVoiceMenuFixture(prefetcherClass(PREFETCH_BODY.replace("const/4 v0, 0x0\n", "const/4 v0, 0x0\nconst/4 v0, 0x0\n"))),
            "a holder that isn't static" to saveVoiceMenuFixture(holderClass(PUBLIC)),
            "a reader with no classifier" to saveVoiceMenuFixture(readerClass(READ_BODY.replace("invoke-virtual {v0, v3, v4}, $ASK\nmove-result-object v1", "const/4 v1, 0x0"))),
            "a kind it doesn't compare" to saveVoiceMenuFixture(readerClass(READ_BODY.replace("sget-object v0, $KIND->A03:$KIND", "const/4 v0, 0x0"))),
            "another kind's field" to saveVoiceMenuFixture(readerClass(READ_BODY.replace("sget-object v0, $KIND->A03:$KIND", "sget-object v0, $KIND->A03:$OBJECT"))),
            "no session-free step" to saveVoiceMenuFixture(classifierClass(withStep = false)),
            "a step the session one doesn't call" to saveVoiceMenuFixture(classifierClass("const/4 v0, 0x0\nreturn-object v0")),
            "a private kind" to saveVoiceMenuFixture(kindClass().let { fixtureClass(KIND, extraFields = listOf(field(KIND, "A03", KIND, AccessFlags.PRIVATE.value or AccessFlags.STATIC.value))) }),
            "a private Uri" to saveVoiceMenuFixture(audioDataClass(privateField)),
            "no download" to saveVoiceMenuFixture(fixtureClass(AUDIO_FETCHER, listOf(constructor(AUDIO_FETCHER)), extraFields = listOf(field(AUDIO_FETCHER, "A0B", PREFETCHER)))),
        )
        for ((case, classes) in cases) {
            assertEquals(emptySet(), found(classes), case)
            assertFailsWith<PatchException>(case) { validateControls(mirroredControls(classes), setOf(SAVE_VOICE_MENU)) }
        }
    }

    @Test fun twoTapsAreTooManyToTrust() {
        val other = "LX/9qS;"
        val second = fixtureClass(other, listOf(tapMethod(MENU_TAP.replace(TAP_OWNER, other), TAP_BODY.replace(TAP_OWNER, other))),
            extraFields = listOf(field(other, "A00", OBJECT), field(other, "A01", "I")))
        assertEquals(emptySet(), found(saveVoiceMenuFixture() + second))
    }

    @Test fun anotherMethodThatOnlyLooksLikeTheTapIsLeftAlone() {
        val other = "LX/9qS;"
        val named = fixtureClass(other, listOf(fixtureMethod("$other->E0b($MENU_ITEM$OBJECT)Z", "const-string v0, \"$MENU_TAP_NAME\"\nconst/4 v0, 0x0\nreturn v0", 5)))
        assertEquals(setOf(MENU_ROWS_CTOR, MENU_TAP), found(saveVoiceMenuFixture() + named))
    }

    @Test fun aNewMenusRowsPassTheExtensionOnTheirWayIntoTheMenu() {
        val method = rowsMethod()
        val before = method.code()
        injectControl(SAVE_VOICE_MENU, mapOf(SAVE_VOICE_MENU to listOf(method)))
        val code = method.code()
        assertEquals(before.size + 3, code.size)
        val at = before.indexOfFirst { it.reference() == COPY_OF }

        assertEquals(Opcode.IGET_OBJECT, code[at].opcode)
        assertEquals("$PARAMS_BUILDER->A01:$OBJECT", code[at].reference())
        assertEquals(3, (code[at] as TwoRegisterInstruction).registerA, "The copy's result register holds nothing yet")
        assertEquals(6, (code[at] as TwoRegisterInstruction).registerB)
        assertEquals(SAVE_VOICE_MENU_ROWS, code[at + 1].reference())
        assertEquals(listOf(0, 3), code[at + 1].arguments())
        assertEquals(Opcode.MOVE_RESULT_OBJECT, code[at + 2].opcode)
        assertEquals(0, (code[at + 2] as OneRegisterInstruction).registerA)
        assertEquals(COPY_OF, code[at + 3].reference())
        assertEquals(listOf(0), code[at + 3].arguments(), "The menu copies what the extension gave back")
        // Everything else is Messenger's own code, unchanged.
        assertEquals(before.map { it.opcode }, code.filterIndexed { i, _ -> i !in at until at + 3 }.map { it.opcode })
    }

    @Test fun theExtensionHearsAboutATapBeforeMessengerStartsOnIt() {
        val method = tapMethod()
        val before = method.code()
        injectControl(SAVE_VOICE_MENU, mapOf(SAVE_VOICE_MENU to listOf(method)))
        val code = method.code()
        assertEquals(before.size + 5, code.size)
        val at = before.indexOfFirst { (it as? ReferenceInstruction)?.reference?.toString() == MENU_TAP_NAME }

        val call = code[at]
        assertEquals(Opcode.INVOKE_STATIC_RANGE, call.opcode)
        assertEquals(SAVE_VOICE_MENU_CLICKED, call.reference())
        assertEquals(20, (call as RegisterRangeInstruction).startRegister, "This, the row and the Bundle")
        assertEquals(3, call.registerCount)
        assertEquals(Opcode.MOVE_RESULT, code[at + 1].opcode)
        assertEquals(0, (code[at + 1] as OneRegisterInstruction).registerA)
        assertEquals(Opcode.IF_EQZ, code[at + 2].opcode)
        assertEquals(at + 5, code.branchTarget(at + 2), "Any other row goes on to Messenger's own code")
        assertEquals(Opcode.CONST_4, code[at + 3].opcode)
        assertEquals(1, (code[at + 3] as NarrowLiteralInstruction).narrowLiteral, "Ours counts as handled")
        assertEquals(Opcode.RETURN, code[at + 4].opcode)
        assertEquals(MENU_TAP_NAME, code[at + 5].reference())
        // The branch that isn't this menu's still skips all of it.
        val skip = code.indexOfFirst { it.opcode == Opcode.IF_EQZ }
        assertTrue(skip < at)
        assertEquals(code.indexOfLast { it.opcode == Opcode.CONST_4 }, code.branchTarget(skip))
    }

    @Test fun anUnusableMethodRefusesTheSwitchBeforeAnyEdit() {
        val cases = mapOf(
            "another constructor" to fixtureMethod("$MENU_PARAMS-><init>(Landroid/os/Parcel;)V", "return-void", 2),
            "a tap that reads no message" to tapMethod(body = TAP_BODY.replace(MENU_ROW_MESSAGE, "row")),
            "the download" to saveVoiceFixture().single().methods.first().let { MutableMethod(it) },
        )
        for ((case, bad) in cases) {
            val before = bad.code()
            assertFailsWith<PatchException>(case) { injectControl(SAVE_VOICE_MENU, mapOf(SAVE_VOICE_MENU to listOf(bad))) }
            assertEquals(before, bad.code(), case)
        }
    }

    private fun built(id: String): List<String> {
        val recipe = assertNotNull(resolveSaveVoiceMenu(saveVoiceMenuFixture()))
        return saveVoiceMenuBuilder(saveVoiceMenuStub(id), recipe).code().mapNotNull { it.reference() }
    }

    @Test fun theRowIsBuiltTheWayMessengerBuildsItsSaveVideoRow() {
        val recipe = assertNotNull(resolveSaveVoiceMenu(saveVoiceMenuFixture()))
        val row = saveVoiceMenuBuilder(saveVoiceMenuStub(SAVE_VOICE_MENU_ROW), recipe)
        assertEquals(listOf(ITEM_BUILDER, "$ITEM_BUILDER-><init>()V", recipe.builderId, recipe.builderTitle, recipe.builderIcon, MENU_SAVE_VIDEO, recipe.builderTag,
            MENU_ITEM, "$MENU_ITEM-><init>($ITEM_BUILDER)V"), row.code().mapNotNull { it.reference() })
        assertEquals(ICON, row.code().firstNotNullOf { it as? NarrowLiteralInstruction }.narrowLiteral)
        assertEquals(4, row.implementation!!.registerCount)
    }

    @Test fun theOtherStubsReadWhatTheRecipeSays() {
        val recipe = assertNotNull(resolveSaveVoiceMenu(saveVoiceMenuFixture()))
        assertEquals(listOf(MENU_ITEM, recipe.itemId), built(SAVE_VOICE_MENU_ROW_ID))
        assertEquals(listOf(TAP_OWNER, recipe.tapHandler, HANDLER, recipe.session), built(SAVE_VOICE_MENU_SESSION))
        assertEquals(listOf(AUDIO_FETCHER, "$AUDIO_FETCHER-><init>()V", recipe.prefetcher, recipe.holder, recipe.holderGet, READER, recipe.classifier, MESSAGE,
            CLASSIFY, recipe.audioKind), built(SAVE_VOICE_MENU_IS_VOICE))
        assertEquals(listOf(AUDIO_FETCHER, "$AUDIO_FETCHER-><init>()V", recipe.prefetcher, recipe.holder, recipe.holderGet, READER, FB_USER_SESSION, MESSAGE, READ_AUDIO,
            recipe.audioUri, AUDIO_REQUEST, "$AUDIO_REQUEST-><init>(Z$URI)V", VOICE_DOWNLOAD), built(SAVE_VOICE_MENU_FETCH))
    }

    @Test fun aFetchAsksForTheFileAsAPlayWouldNotAsAPrefetch() {
        val recipe = assertNotNull(resolveSaveVoiceMenu(saveVoiceMenuFixture()))
        val code = saveVoiceMenuBuilder(saveVoiceMenuStub(SAVE_VOICE_MENU_FETCH), recipe).code()
        val make = code.indexOfFirst { it.reference() == "$AUDIO_REQUEST-><init>(Z$URI)V" }
        val flag = code[make - 1] as NarrowLiteralInstruction
        assertEquals(0, flag.narrowLiteral)
        assertEquals((flag as OneRegisterInstruction).registerA, code[make].arguments()[1])
    }

    @Test fun aChangedStubStopsThePatch() {
        val recipe = assertNotNull(resolveSaveVoiceMenu(saveVoiceMenuFixture()))
        val flags = AccessFlags.STATIC.value
        val cases = mapOf(
            "a real body" to fixtureMethod(SAVE_VOICE_MENU_ROW, "const/4 v0, 0x1\nreturn-object v0", 3, flags),
            "a longer body" to fixtureMethod(SAVE_VOICE_MENU_ROW_ID, "const/4 v0, 0x0\nconst/4 v0, 0x0\nreturn v0", 2, flags),
            "not static" to fixtureMethod(SAVE_VOICE_MENU_SESSION, "const/4 v0, 0x0\nreturn-object v0", 2, PUBLIC),
            "private" to fixtureMethod(SAVE_VOICE_MENU_FETCH, "const/4 v0, 0x0\nreturn-object v0", 3, flags or AccessFlags.PRIVATE.value),
            "a method with no stub" to fixtureMethod("$SAVE_VOICE_MENU_HOST->other()Ljava/lang/Object;", "const/4 v0, 0x0\nreturn-object v0", 1, flags),
        )
        for ((case, stub) in cases) assertFailsWith<PatchException>(case) { saveVoiceMenuBuilder(stub, recipe) }
    }

    @Test fun withoutARecipeNothingIsFound() {
        saveVoiceMenuRecipe = assertNotNull(resolveSaveVoiceMenu(saveVoiceMenuFixture()))
        assertTrue(findSaveVoiceMenu(saveVoiceMenuFixture().filter { it.type != READER }).isEmpty())
        assertNull(saveVoiceMenuRecipe, "A build it can't read leaves no recipe behind from an earlier one")
    }

    @Test fun everySupportedBuildReadsItsOwnPieces() {
        val root = System.getenv("HUSH_NATIVE_FIXTURES")
        assumeTrue(root != null, "Set HUSH_NATIVE_FIXTURES to the exact stock fixture directory")
        val apks = Files.list(Path.of(root!!)).use { it.filter { p -> p.toString().endsWith(".apk") }.sorted().toList() }
        assertEquals(controlProfiles.size, apks.size)
        for (apk in apks) {
            val code = apk.fileName.toString().substringBeforeLast(".apk").substringAfterLast('-')
            activeProfile = controlProfileFor(code)
            val dex = DexFileFactory.loadDexContainer(apk.toFile(), Opcodes.forApi(35))
            val sites = findSaveVoiceMenu(dex.dexEntryNames.flatMap { dex.getEntry(it)!!.dexFile.classes })
            assertEquals(activeProfile.hooks.getValue(SAVE_VOICE_MENU), sites.map { it.hookId() }.toSet(), code)
            validateControls(mapOf(SAVE_VOICE_MENU to sites), setOf(SAVE_VOICE_MENU))
            val recipe = assertNotNull(saveVoiceMenuRecipe, code)
            for (id in SAVE_VOICE_MENU_STUBS) saveVoiceMenuBuilder(saveVoiceMenuStub(id), recipe)
            for (site in sites) MutableMethod(site).injectSaveVoiceMenu()
        }
    }
}
