package me.jonycape.dev.flamecore.promocode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Color;

import java.util.List;

@Getter
@AllArgsConstructor
public final class PromoCode {

    private final String code;
    private final int uses;
    private final List<String> commands;
    private final List<Color> colors;
    private final List<Color> fade;
}