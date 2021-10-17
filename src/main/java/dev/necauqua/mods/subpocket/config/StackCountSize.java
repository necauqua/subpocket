package dev.necauqua.mods.subpocket.config;

public enum StackCountSize implements IMnenonic<StackCountSize> {
    LARGE("lrg"),
    MIXED("mxd"),
    SMALL("sml");

    final String mnemonic;

    StackCountSize(String mnemonic) {
        this.mnemonic = mnemonic;
    }

    public boolean applies(boolean shortSize) {
        return this == LARGE || shortSize && this == MIXED;
    }

    @Override
    public String mnemonic() {
        return mnemonic;
    }

    @Override
    public StackCountSize next() {
        var values = StackCountSize.values();
        return values[(ordinal() + 1) % values.length];
    }
}
