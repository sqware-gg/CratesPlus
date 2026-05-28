package dev.cratesplus.crate;

import java.util.List;

public record CrateMilestone(int openings, boolean repeatable, String message, List<String> commands) {
    public CrateMilestone {
        commands = List.copyOf(commands);
    }
}
