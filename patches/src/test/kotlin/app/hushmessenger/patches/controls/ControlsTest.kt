package app.hushmessenger.patches.controls

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.*

class ControlsTest {
    private fun method(registers: Int = 2, body: String = "const/4 v0, 0x1\nreturn v0", returnType: String = "Z") = MutableMethod(
        ImmutableMethod("Lfixture/Gate;", "gate", emptyList(), returnType, AccessFlags.PUBLIC.value,
            null, null, ImmutableMethodImplementation(registers, emptyList(), null, null)),
    ).apply { addInstructionsWithLabels(0, body) }

    @Test fun optOutBranchesToTheUnmodifiedOriginalFirstInstruction() {
        val gate = method()
        val original = gate.implementation!!.instructions.toList()
        gate.injectSwitch("hideStories", "0x0")
        val code = gate.implementation!!.instructions
        assertEquals(Opcode.IF_EQZ, code[2].opcode)
        val targetAddress = code.take(2).sumOf { it.codeUnits } + (code[2] as OffsetInstruction).codeOffset
        assertEquals(code.take(5).sumOf { it.codeUnits }, targetAddress)
        assertEquals(original, code.drop(5))
    }

    @Test fun methodsWithNoScratchLocalFailBeforeChangingParameters() {
        val gate = method(registers = 1, body = "const/4 v0, 0x0\nreturn v0")
        val original = gate.implementation!!.instructions.toList()
        assertFailsWith<PatchException> { gate.injectSwitch("hideStories", "0x0") }
        assertEquals(original, gate.implementation!!.instructions.toList())
    }

    @Test fun typingHookRetainsTheOriginalRunnableAndReturnsOnlyWhenEnabled() {
        val runnable = method(body = "return-void", returnType = "V")
        runnable.injectSwitch("suppressTyping", "0x0")
        assertEquals(listOf(Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT, Opcode.IF_EQZ, Opcode.RETURN_VOID, Opcode.RETURN_VOID),
            runnable.implementation!!.instructions.map { it.opcode })
    }

    @Test fun missingOrAmbiguousAnchorsRejectTheApk() {
        assertFailsWith<PatchException> { validateControls(emptyMap()) }
        assertFailsWith<PatchException> { validateControls(syntheticHooks.keys.associateWith { listOf(method(), method()) }) }
    }

    @Test fun changedSubtabAndBrowserBodiesFail() {
        assertFailsWith<PatchException> { method().injectSubtabs() }
        assertFailsWith<PatchException> { method().injectBrowserPreference() }
    }

    @Test fun subtabFlagMustBeTheValuePassedToItsSetter() {
        val supplier = "Lcom/facebook/messaging/inboxsubtabs/plugins/subtabs/itemsupplier/InboxSubtabsItemSupplierImplementation;"
        fun subtabs(value: String) = method(registers = 3, returnType = "V", body = """
            iget-object v0, p0, LX/2UL;->A00:$supplier
            iget-object v1, v0, $supplier->A07:Ljava/util/concurrent/atomic/AtomicBoolean;
            const/4 v0, 0x1
            invoke-virtual {v1, $value}, Ljava/util/concurrent/atomic/AtomicBoolean;->set(Z)V
            return-void
        """.trimIndent())
        val original = subtabs("v0")
        original.injectSubtabs()
        assertEquals(7, original.implementation!!.instructions.size)
        val changed = subtabs("v2")
        assertFailsWith<PatchException> { changed.injectSubtabs() }
        assertEquals(5, changed.implementation!!.instructions.size)
    }

    @Test fun unsentLabelHookReadsTheRowStraightFromTheWrapperList() {
        messageWrapperFixture().methods.single { it.name == "BWo" }.validateUnsentIndicator()
        // Older releases went through a static helper instead, a shape no supported build has.
        val helper = fixtureMethod("Lfixture/MessageWrapper;->BWo(I)Ljava/lang/String;", """
            invoke-static {p0, p1}, Lfixture/MessageWrapper;->A00(Lfixture/MessageWrapper;I)Lfixture/KKn;
            move-result-object v0
            invoke-interface {v0}, Lfixture/KKn;->BWn()Ljava/lang/String;
            move-result-object v0
            return-object v0
        """.trimIndent(), registers = 3)
        assertFailsWith<PatchException> { helper.validateUnsentIndicator() }
        val otherRegister = fixtureMethod("Lfixture/MessageWrapper;->BWo(I)Ljava/lang/String;", """
            iget-object v0, p0, Lfixture/MessageWrapper;->A00:Ljava/util/List;
            invoke-interface {v0, p1}, Ljava/util/List;->get(I)Ljava/lang/Object;
            move-result-object v0
            check-cast v0, Lfixture/KKn;
            invoke-interface {v0}, Lfixture/KKn;->BWn()Ljava/lang/String;
            move-result-object p1
            return-object p1
        """.trimIndent(), registers = 3)
        assertFailsWith<PatchException> { otherRegister.validateUnsentIndicator() }
    }

    @Test fun unsentFlagHookMustAnswerFromV0OfAnInstanceMethod() {
        messageWrapperFixture().methods.single { it.name == "Btd" }.validateDeltaUnsent()
        fun flag(body: String, registers: Int = 3, flags: Int = AccessFlags.PUBLIC.value) =
            fixtureMethod("Lfixture/MessageWrapper;->Btd(I)Z", body.trimIndent(), registers, flags)
        val stock = """
            invoke-static {p0, p1}, Lfixture/MessageWrapper;->A00(Lfixture/MessageWrapper;I)Lfixture/KKn;
            move-result-object v0
            invoke-interface {v0}, Lfixture/KKn;->Btc()Z
            move-result v0
            return v0
        """
        flag(stock).validateDeltaUnsent()
        val otherRegister = stock.replace("move-result v0", "move-result p1").replace("return v0", "return p1")
        assertFailsWith<PatchException> { flag(otherRegister).validateDeltaUnsent() }
        assertFailsWith<PatchException> { flag(stock, registers = 4).validateDeltaUnsent() }
        val static = stock.replace("{p0, p1}", "{p0, v1}")
        assertFailsWith<PatchException> { flag(static, flags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value).validateDeltaUnsent() }
    }

    @Test fun keptUnsentHookMustBeAnInstanceMethodWithItsIntentInReach() {
        fun revoke(registers: Int, flags: Int = AccessFlags.PUBLIC.value) = fixtureMethod(
            "Lfixture/Revoke;->A00(Landroid/content/Intent;Ljava/lang/String;Ljava/lang/Object;)V", "return-void", registers, flags)
        revoke(5).validateKeepUnsent()
        assertFailsWith<PatchException> { revoke(5, AccessFlags.PUBLIC.value or AccessFlags.STATIC.value).validateKeepUnsent() }
        // p1 lands on v15 with 18 registers and on v16 with 19, where a plain invoke can't reach it.
        revoke(18).validateKeepUnsent()
        assertFailsWith<PatchException> { revoke(19).validateKeepUnsent() }
    }

    @Test fun settingsHaveALauncherEntryAndAPrivateProviderWithoutChangingHostPermissions() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity android:name="stock.Activity" android:permission="stock.permission" /></application></manifest>""".toByteArray(),
        ))
        document.addSettingsEntry()
        val provider = document.getElementsByTagName("provider").item(0) as org.w3c.dom.Element
        assertEquals("false", provider.getAttribute("android:exported"))
        val activities = document.getElementsByTagName("activity")
        assertEquals("stock.permission", (activities.item(0) as org.w3c.dom.Element).getAttribute("android:permission"))
        val settings = activities.item(1) as org.w3c.dom.Element
        assertEquals("HushMessenger settings", settings.getAttribute("android:label"))
        // Only the alias carries the launcher filter, so hiding it leaves the activity reachable.
        assertEquals(0, settings.getElementsByTagName("intent-filter").length)
        val alias = document.getElementsByTagName("activity-alias").item(0) as org.w3c.dom.Element
        assertEquals("app.hushmessenger.extension.SettingsLauncher", alias.getAttribute("android:name"))
        assertEquals("app.hushmessenger.extension.SettingsActivity", alias.getAttribute("android:targetActivity"))
        assertEquals("android.intent.category.LAUNCHER", (alias.getElementsByTagName("category").item(0) as org.w3c.dom.Element).getAttribute("android:name"))
        assertFailsWith<PatchException> { document.addSettingsEntry() }
    }

    @Test fun notificationsSuggestionsJoinTheStockPreferenceAndSkipTheServerOverride() {
        val serverBranch = 20
        val reader = peopleJewelMethod()
        val original = reader.implementation!!.instructions.toList()
        assertEquals(Opcode.IF_NEZ, original[serverBranch].opcode)
        reader.injectPeopleSection()
        val code = reader.implementation!!.instructions.toList()
        fun assertSwitch(index: Int, method: String) {
            assertEquals("$SETTINGS->$method(Z)Z", (code[index] as ReferenceInstruction).reference.toString())
            assertEquals(listOf(1, 0), (code[index] as FiveRegisterInstruction).let { listOf(it.registerCount, it.registerC) })
            assertEquals(Opcode.MOVE_RESULT, code[index + 1].opcode)
            assertEquals(0, (code[index + 1] as OneRegisterInstruction).registerA)
        }
        assertEquals(original.take(12), code.take(12))
        assertSwitch(12, "hidePeopleSection")
        assertEquals(original.subList(12, serverBranch), code.subList(14, serverBranch + 2))
        assertSwitch(serverBranch + 2, "keepPeopleSection")
        assertEquals(original.drop(serverBranch), code.drop(serverBranch + 4))
        // Both stock branches still skip to the original "not hidden" return.
        assertEquals(code.lastIndex, code.branchTarget(14))
        assertEquals(code.lastIndex, code.branchTarget(serverBranch + 4))
    }

    @Test fun changedNotificationsSuggestionsReaderFailsBeforeEditing() {
        for (changed in listOf(
            peopleJewelMethod(resultRegister = "v3"),
            peopleJewelMethod(key = "LX/JTx;->A00:LX/1BL;"),
            peopleJewelMethod(flags = AccessFlags.PUBLIC.value),
            peopleJewelMethod(serverFlag = "0x1L"),
            peopleJewelMethod(serverTarget = ":hidden"),
            // A second load of the flag makes the server branch ambiguous.
            peopleJewelMethod(extraFlag = true),
            // Older readers: one call for the list reset moves the flag to 16, and one without the logger.
            peopleJewelMethod(inlinedReset = true),
            peopleJewelMethod(logged = false),
        )) {
            val before = changed.implementation!!.instructions.toList()
            assertFailsWith<PatchException> { changed.injectPeopleSection() }
            assertEquals(before, changed.implementation!!.instructions.toList())
        }
    }

    @Test fun peopleTabListenerGetsEmptySuggestionsOnlyWhileTheSwitchIsOn() {
        val publish = peopleTabMethod()
        val original = publish.implementation!!.instructions.toList()
        publish.injectPeopleTab()
        val code = publish.implementation!!.instructions.toList()
        fun ref(index: Int) = (code[index] as ReferenceInstruction).reference.toString()
        assertEquals("$SETTINGS->enabled(Ljava/lang/String;)Z", ref(1))
        assertEquals(Opcode.IF_EQZ, code[3].opcode)
        assertEquals(11, code.branchTarget(3))
        assertEquals(original, code.drop(11))
        assertEquals("LX/JZ6;->A09:LX/KIH;", ref(4))
        assertEquals("$IMMUTABLE_LIST->of()$IMMUTABLE_LIST", ref(5))
        assertEquals("Ljava/util/Collections;->emptyMap()Ljava/util/Map;", ref(7))
        assertEquals("LX/KIH;->CbW(${IMMUTABLE_LIST}Ljava/util/Map;)V", ref(9))
        assertEquals(listOf(3, 2, 1, 0), (code[9] as FiveRegisterInstruction).let { listOf(it.registerCount, it.registerC, it.registerD, it.registerE) })
        assertEquals(Opcode.RETURN_VOID, code[10].opcode)
    }

    @Test fun searchSuggestionsSourceReturnsNoSectionsOnlyWhileTheSwitchIsOn() {
        val source = peopleSearchMethod()
        val original = source.implementation!!.instructions.toList()
        val at = original.indexOfFirst { it.opcode == Opcode.SGET_OBJECT }
        source.injectPeopleSearch()
        val code = source.implementation!!.instructions.toList()
        assertEquals(original.take(at), code.take(at))
        assertEquals("$SETTINGS->enabled(Ljava/lang/String;)Z", (code[at + 1] as ReferenceInstruction).reference.toString())
        // The check borrows the status register, which the untouched status load overwrites next.
        assertEquals(0, (code[at + 2] as OneRegisterInstruction).registerA)
        assertEquals(at + 6, code.branchTarget(at + 3))
        assertEquals("$IMMUTABLE_LIST->of()$IMMUTABLE_LIST", (code[at + 4] as ReferenceInstruction).reference.toString())
        assertEquals(1, (code[at + 5] as OneRegisterInstruction).registerA)
        assertEquals(original.drop(at), code.drop(at + 6))
    }

    @Test fun changedSearchSuggestionsSourceFailsBeforeEditing() {
        for (changed in listOf(
            peopleSearchMethod(flags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value),
            peopleSearchMethod(status = "LX/0R2;->A0N:Ljava/lang/Long;"),
            peopleSearchMethod(wrap = "LX/CW4;->A0m(${IMMUTABLE_LIST}Ljava/lang/Object;)LX/EBu;"),
            peopleSearchMethod(wrap = "LX/CW4;->A0m(${IMMUTABLE_LIST}Ljava/lang/Integer;)LX/EBv;"),
            peopleSearchMethod(statusRegister = "v2"),
            // A path that joins at the status load would skip the check.
            peopleSearchMethod(jumpToStatus = true),
        )) {
            val before = changed.implementation!!.instructions.toList()
            assertFailsWith<PatchException> { changed.injectPeopleSearch() }
            assertEquals(before, changed.implementation!!.instructions.toList())
        }
    }

    @Test fun storyViewerSkipsItsSuggestionsRequestOnlyWhileTheSwitchIsOn() {
        val viewer = peopleStoryMethod()
        val original = viewer.implementation!!.instructions.toList()
        viewer.injectPeopleStory()
        val code = viewer.implementation!!.instructions.toList()
        assertEquals(original.take(4), code.take(4))
        assertEquals("people", (code[4] as ReferenceInstruction).reference.toString())
        assertEquals("$SETTINGS->enabled(Ljava/lang/String;)Z", (code[5] as ReferenceInstruction).reference.toString())
        // The check reuses the flag register, so both ways out leave it as the stock check would.
        assertEquals(0, (code[6] as OneRegisterInstruction).registerA)
        assertEquals(Opcode.IF_NEZ, code[7].opcode)
        assertEquals(0, (code[7] as OneRegisterInstruction).registerA)
        assertEquals(code.branchTarget(3), code.branchTarget(7))
        assertEquals(Opcode.RETURN_VOID, code[code.branchTarget(7)].opcode)
        assertEquals(original.drop(4), code.drop(8))
    }

    @Test fun changedStoryViewerRequestFailsBeforeEditing() {
        for (changed in listOf(
            peopleStoryMethod(flags = AccessFlags.PUBLIC.value),
            peopleStoryMethod(checkedFlag = "A0w"),
            peopleStoryMethod(skip = "if-eqz"),
            // A path that joins right after the check would skip it.
            peopleStoryMethod(jumpPastCheck = true),
        )) {
            val before = changed.implementation!!.instructions.toList()
            assertFailsWith<PatchException> { changed.injectPeopleStory() }
            assertEquals(before, changed.implementation!!.instructions.toList())
        }
    }

    @Test fun changedPeopleTabPublishFailsBeforeEditing() {
        for (changed in listOf(
            peopleTabMethod(flags = AccessFlags.PUBLIC.value),
            peopleTabMethod(listenerRegister = "v1"),
            peopleTabMethod(listener = "LX/JZ6;->A08:Landroid/content/Context;"),
            peopleTabMethod(call = "LX/KIH;->CbW(${IMMUTABLE_LIST}Ljava/util/List;)V"),
        )) {
            val before = changed.implementation!!.instructions.toList()
            assertFailsWith<PatchException> { changed.injectPeopleTab() }
            assertEquals(before, changed.implementation!!.instructions.toList())
        }
    }

    @Test fun browserInjectionRejectsStaticMethodsBeforeUsingTheWrongUriParameter() {
        val browser = MutableMethod(ImmutableMethod(
            "Lcom/facebook/messaging/browser/util/MessengerBrowserLauncher;", "A0L",
            listOf("Landroid/net/Uri;", "Lcom/facebook/auth/usersession/FbUserSession;")
                .map { ImmutableMethodParameter(it, null, null) },
            "Z", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, null, null,
            ImmutableMethodImplementation(9, emptyList(), null, null),
        )).apply {
            addInstructionsWithLabels(0, "nop\n".repeat(60) + """
                sget-object v0, LX/1D1;->A1U:LX/1BK;
                invoke-interface {v1, v0, v3}, Lcom/facebook/prefs/shared/FbSharedPreferences;->AhC(LX/1BK;Z)Z
                move-result v0
                if-eqz v0, :stock
                return v0
                :stock
                return v0
            """.trimIndent())
        }
        val before = browser.implementation!!.instructions.toList()
        assertFailsWith<PatchException> { browser.injectBrowserPreference() }
        assertEquals(before, browser.implementation!!.instructions.toList())
    }
}
