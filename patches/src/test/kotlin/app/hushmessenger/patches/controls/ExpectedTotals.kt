package app.hushmessenger.patches.controls

/**
 * Catalog-wide totals the tests check, kept in one place so a new control changes one file. They're set by hand on
 * purpose: a control that drops out or a hook set that changes size has to show up here as a failing test.
 */
internal object ExpectedTotals {
    /** Settings controls, each its own patch. */
    const val CONTROLS = 42
    /** Controls whose description sends people to HushMessenger settings > Controls. */
    const val DIRECTED_CONTROLS = 41
    /** Hook methods per 582 build. Nine of them read the emoji drawer flag. */
    const val HOOKS_582 = 144
    /** The complete synthetic discovery fixture, one method per expected hook. */
    const val DISCOVERY_FIXTURE_HOOKS = 137
}
