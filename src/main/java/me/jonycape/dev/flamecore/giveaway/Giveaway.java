package me.jonycape.dev.flamecore.giveaway;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
public final class Giveaway {

    private final String id;
    private final String prize;
    private final long endTime;

    @Builder.Default
    private List<String> participants = new ArrayList<>();
}