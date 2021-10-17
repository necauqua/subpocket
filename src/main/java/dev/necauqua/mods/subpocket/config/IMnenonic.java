package dev.necauqua.mods.subpocket.config;

public interface IMnenonic<Self extends IMnenonic<Self>> {

    String mnemonic();

    Self next();
}
