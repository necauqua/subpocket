package dev.necauqua.mods.subpocket.config;

public enum PickingMode implements IMnenonic<PickingMode> {
    PIXEL("pxl"),
    ALTERNATIVE("alt");

    final String mnemonic;

    PickingMode(String mnemonic) {
        this.mnemonic = mnemonic;
    }

    public boolean isAlt() {
        return this == ALTERNATIVE;
    }

    @Override
    public String mnemonic() {
        return mnemonic;
    }

    @Override
    public PickingMode next() {
        var values = PickingMode.values();
        return values[(ordinal() + 1) % values.length];
    }
}
