package com.acme.blueprint.cli;

/**
 * One entry of a list to pick from.
 *
 * @param id      what is typed, where keys cannot be read, and what the answer is
 * @param label   what the menu shows
 * @param note    shown after the label in parentheses — why it is the default, what it brings; null for none
 * @param enabled false for what is coming but not there yet: shown, never picked
 */
record Choice(String id, String label, String note, boolean enabled) {

    static Choice of(String id, String label) {
        return new Choice(id, label, null, true);
    }

    static Choice of(String id, String label, String note) {
        return new Choice(id, label, note, true);
    }

    /** Shown greyed out, with {@code (soon)}. */
    static Choice soon(String id, String label) {
        return new Choice(id, label, "soon", false);
    }

    /** As shown: the label and the note. */
    String described() {
        return note == null ? label : label + " (" + note + ")";
    }
}
