package com.acme.blueprint.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;

/**
 * The user's terminal through JLine: lines with editing, and raw key presses
 * for the menus. Ctrl-C cancels instead of killing the process, so the
 * terminal is always left as it was found.
 */
final class TerminalKeyboard implements Keyboard, AutoCloseable {

    private final Terminal terminal;
    private final LineReader lines;
    private final KeyMap<Key> keys = new KeyMap<>();
    private final BindingReader bindings;

    private TerminalKeyboard(Terminal terminal) {
        this.terminal = terminal;
        this.lines = LineReaderBuilder.builder().terminal(terminal)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();
        this.bindings = new BindingReader(terminal.reader());
        bind(Key.UP, KeyMap.key(terminal, Capability.key_up), "\u001B[A", "\u001BOA", "k");
        bind(Key.DOWN, KeyMap.key(terminal, Capability.key_down), "\u001B[B", "\u001BOB", "j");
        bind(Key.ENTER, "\r", "\n");
        bind(Key.SPACE, " ");
        bind(Key.INTERRUPT, KeyMap.ctrl('C'));
        bind(Key.END, KeyMap.ctrl('D'));
        keys.setNomatch(Key.OTHER);
        keys.setAmbiguousTimeout(100);
    }

    /** The system terminal; empty when there is none that can read single keys. */
    static Optional<TerminalKeyboard> open() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        try {
            // JNI only for Windows' console; everywhere else (Git Bash's mintty included) stty does it.
            Terminal terminal = TerminalBuilder.builder().system(true).dumb(true)
                    .jni(windows).exec(true).ffm(false).jna(false).jansi(false)
                    .build();
            if (terminal.getType().startsWith(Terminal.TYPE_DUMB)) {
                terminal.close();
                return Optional.empty();
            }
            return Optional.of(new TerminalKeyboard(terminal));
        } catch (IOException | RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    @Override
    public String readLine(String prompt) {
        try {
            return lines.readLine(prompt);
        } catch (UserInterruptException interrupted) {
            throw new PromptCancelledException();
        } catch (EndOfFileException ended) {
            return null;
        }
    }

    @Override
    public Optional<KeyPresses> keyPresses() {
        Attributes saved = terminal.enterRawMode();
        Attributes raw = terminal.getAttributes();
        raw.setLocalFlag(Attributes.LocalFlag.ISIG, false);   // Ctrl-C arrives as a key
        terminal.setAttributes(raw);
        return Optional.of(new KeyPresses() {
            @Override
            public Key next() {
                Key key = bindings.readBinding(keys);
                return key != null ? key : Key.END;
            }

            @Override
            public PrintWriter screen() {
                return terminal.writer();
            }

            @Override
            public int width() {
                return terminal.getWidth();
            }

            @Override
            public void close() {
                terminal.setAttributes(saved);
            }
        });
    }

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException ignored) {
            // Leaving anyway.
        }
    }

    private void bind(Key key, String... sequences) {
        Stream.of(sequences).filter(Objects::nonNull).forEach(sequence -> keys.bind(key, sequence));
    }
}
