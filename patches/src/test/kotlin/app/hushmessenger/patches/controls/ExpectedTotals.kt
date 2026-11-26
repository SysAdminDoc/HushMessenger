package app.hushmessenger.patches.controls

/**
 * Catalog-wide totals the tests check, kept in one place so a new control changes one file. They're set by hand on
 * purpose: a control that drops out or a hook set that changes size has to show up here as a failing test.
 */
internal object ExpectedTotals {
    /** Settings controls, each its own patch. */
    const val CONTROLS = 123
    /** Controls whose description sends people to HushMessenger settings > Controls. */
    const val DIRECTED_CONTROLS = 122
    /** Controls declared through controlPatch, whose injection HookInjectionReplayTest replays on the real builds. */
    const val REPLAYED_CONTROLS = 118
    /**
     * Hook methods per 588 build, armv7 included (587: 324, with five AI mention rows to 588's six). Three of them read
     * the emoji drawer flag (586: seven), three hold back the active status (#243), one picks how a sent video
     * draws (#104), one opens the chat sounds (#224), and two send the words before a photo the editor sends by itself
     * (#236).
     */
    const val HOOKS_588 = 336
    /** The complete synthetic discovery fixture, one method per expected hook. */
    const val DISCOVERY_FIXTURE_HOOKS = 314
}
