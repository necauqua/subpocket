package dev.necauqua.mods.subpocket.config;

/**
 * Variants of how stack sizes can be shown in the subpocket field.
 */
public enum StackCountCondition implements IMnenonic<StackCountCondition> {
    /**
     * Show all numbers. The default, works nicely at the beggining
     * where there is not a lot of item types stored, but very
     * obstructive later on.
     */
    ALWAYS("alw"),
    /**
     * Show the number only for hovered items, matter of preference
     * between this or {@link #NEVER}.
     */
    HOVER("hvr"),
    /**
     * Don't show numbers at items at all, only way to know the number
     * of items in subpocket stack is by the tooltip.
     */
    NEVER("nvr");

    final String mnemonic;

    StackCountCondition(String mnemonic) {
        this.mnemonic = mnemonic;
    }

    @Override
    public String mnemonic() {
        return mnemonic;
    }

    public boolean applies(boolean hovered) {
        return this == ALWAYS || hovered && this == HOVER;
    }

    @Override
    public StackCountCondition next() {
        var values = StackCountCondition.values();
        return values[(ordinal() + 1) % values.length];
    }
}
