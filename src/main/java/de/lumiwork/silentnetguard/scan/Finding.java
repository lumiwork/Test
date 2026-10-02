package de.lumiwork.silentnetguard.scan;

import java.io.Serializable;

/** Ein einzelner Fund mit Punkten. */
public record Finding(Category category, int points, String text) implements Serializable {
}
