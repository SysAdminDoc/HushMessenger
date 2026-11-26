package app.hushmessenger.patches.controls

/**
 * Catalog-wide totals the tests check, kept in one place so a new control changes one file. They're set by hand on
 * purpose: a control that drops out or a hook set that changes size has to show up here as a failing test.
 */
internal object ExpectedTotals {
    /** Settings controls, each its own patch. */
    const val CONTROLS = 124
    /** Controls whose description sends people to HushMessenger settings > Controls. */
    const val DIRECTED_CONTROLS = 123
    /** Controls declared through controlPatch, whose injection HookInjectionReplayTest replays on the real builds. */
    const val REPLAYED_CONTROLS = 119
    /**
     * Hook methods per 588 build, armv7 included (587: 324, with five AI mention rows to 588's six). Three of them read
     * the emoji drawer flag (586: seven), three hold back the active status (#243), one picks how a sent video
     * draws (#104), one opens the chat sounds (#224), three send the words before a photo the editor sends by itself
     * or hands back (#236), two send a preview card's TikTok and Instagram links past the link player (#246), one ranks the
     * pinned messages bar first (#213), and one holds the first send to someone who isn't a friend (#207).
     */
    const val HOOKS_588 = 341
    /** The complete synthetic discovery fixture, one method per expected hook. */
    const val DISCOVERY_FIXTURE_HOOKS = 319
}
