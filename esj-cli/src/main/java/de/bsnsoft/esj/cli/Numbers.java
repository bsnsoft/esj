package de.bsnsoft.esj.cli;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * The numbers the limit switches take: a count of bytes, on its own or with a {@code k},
 * {@code M} or {@code G} suffix, and the duration of {@code --max-runtime}.
 *
 * <p>The suffixes are multiples of 1024 rather than of 1000, because every bound they set
 * is stated in mebibytes in the specification, section 12.2, and a switch that halved the
 * unit of its own page would be a trap. They are read in either case, so that
 * {@code 256M} and {@code 256m} are the same number.
 *
 * <p>A value that is not a number at all is refused while the command line is being read:
 * that is a mistake in the request rather than a document that outgrew something, so it
 * leaves with {@link ExitCode#INPUT} and picocli puts the name of the switch in front of
 * the sentence. A value that is a number no configuration can hold is refused by
 * {@link Bounds}, which is the side that knows what each bound may be.
 */
final class Numbers {

    /** The largest number a switch takes, so that a suffix cannot overflow a bound. */
    static final long MAX = Long.MAX_VALUE / (1024L * 1024L * 1024L);

    /** A duration as a command line writes it: a number and an optional unit. */
    private static final Pattern DURATION = Pattern.compile("([0-9]{1,9})(ms|s|m)?");

    private Numbers() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the number of bytes a value stands for.
     *
     * @param text the value as it was written
     * @return the number, with a suffix applied
     * @throws TypeConversionException if the text is not a number with an optional
     *                                 {@code k}, {@code M} or {@code G} suffix
     */
    static long bytes(String text) {
        String digits = text;
        long multiplier = suffix(text);
        if (multiplier != 1L) {
            digits = text.substring(0, text.length() - 1);
        }
        long value;
        try {
            value = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new TypeConversionException("a number, on its own or with a k, M or G"
                    + " suffix for multiples of 1024, not '" + text + "'");
        }
        if (value < 0 || value > MAX) {
            throw new TypeConversionException("a number between 0 and " + MAX + ", not '"
                    + text + "'");
        }
        return value * multiplier;
    }

    private static long suffix(String text) {
        if (text.isEmpty()) {
            return 1L;
        }
        return switch (Character.toLowerCase(text.charAt(text.length() - 1))) {
            case 'k' -> 1024L;
            case 'm' -> 1024L * 1024L;
            case 'g' -> 1024L * 1024L * 1024L;
            default -> 1L;
        };
    }

    /** Reads the value of a bound: {@code --max-input-bytes} and its six companions. */
    static final class ByteCount implements ITypeConverter<Long> {

        @Override
        public Long convert(String value) {
            return bytes(value);
        }
    }

    /**
     * Reads {@code --max-runtime}.
     *
     * <p>The value is a number with a unit — {@code 500ms}, {@code 90s}, {@code 5m} — or
     * a bare number, which is seconds. The unit is written out rather than inferred from
     * the size of the number, because {@code --max-runtime 30} meaning milliseconds on one
     * tool and seconds on the next is a mistake a user makes once per tool.
     */
    static final class Runtime implements ITypeConverter<Duration> {

        @Override
        public Duration convert(String value) {
            Matcher matcher = DURATION.matcher(value);
            if (!matcher.matches()) {
                throw new TypeConversionException("takes a duration such as 500ms, 90s or"
                        + " 5m, or a bare number of seconds, not '" + value + "'");
            }
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2) == null ? "s" : matcher.group(2);
            Duration duration = switch (unit) {
                case "ms" -> Duration.ofMillis(amount);
                case "m" -> Duration.ofMinutes(amount);
                default -> Duration.ofSeconds(amount);
            };
            if (duration.isZero()) {
                throw new TypeConversionException("is a positive duration, and '" + value
                        + "' is none");
            }
            return duration;
        }

        /**
         * Writes a duration back the way the command line takes it, for the lines that
         * report the bound that was reached.
         *
         * @param duration the bound
         * @return the bound as a number and a unit
         */
        static String text(Duration duration) {
            long millis = duration.toMillis();
            return millis % 1000 == 0 ? millis / 1000 + " s" : millis + " ms";
        }
    }
}
