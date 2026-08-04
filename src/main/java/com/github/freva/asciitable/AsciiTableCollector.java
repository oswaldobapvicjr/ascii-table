package com.github.freva.asciitable;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;

/**
 * Utility collector to convert a Stream of objects into an ASCII table String using {@link AsciiTable}.
 *
 * This implementation is optimized to map each element exactly once to a row (String[])
 * while accumulating. It avoids creating an intermediate {@code List<T>} of original elements.
 *
 * @author oswaldo.bapvic.jr
 * @since 1.13.0
 */
@NullMarked
public final class AsciiTableCollector {
    private AsciiTableCollector() {}

    /**
     * Generic accumulator that maps elements to rows and accumulates them.
     *
     * <p>See the public API docs for threading / parallel-safety guidance.
     */
    private static final class RowAccumulator<T extends @Nullable Object> {
        private final ColumnData<T>[] rawColumns;
        private final @Nullable Character @Nullable[] border;
        private final ArrayList<@Nullable String[]> rows;

        RowAccumulator(ColumnData<T>[] rawColumns, @Nullable Character @Nullable[] border) {
            this.rawColumns = rawColumns;
            this.border = border;
            this.rows = new ArrayList<>();
        }

        void add(T item) {
            final int cols = rawColumns.length;
            @Nullable String[] row = new String[cols];
            for (int i = 0; i < cols; i++) {
                row[i] = rawColumns[i].getCellValue(item);
            }
            rows.add(row);
        }

        RowAccumulator<T> combine(RowAccumulator<T> other) {
            if (other == this) return this;
            if (this.rows.isEmpty()) return other;
            if (other.rows.isEmpty()) return this;
            // reduce reallocation
            this.rows.ensureCapacity(this.rows.size() + other.rows.size());
            this.rows.addAll(other.rows);
            return this;
        }

        String finish() {
            @Nullable String[][] data = rows.toArray(new String[rows.size()][]);
            return border == null ? AsciiTable.getTable(rawColumns, data) : AsciiTable.getTable(border, rawColumns, data);
        }
    }

    /**
     * Factory that creates the optimized collector for the given columns and optional border.
     */
    private static <T extends @Nullable Object> Collector<T, ?, String> createCollector(@Nullable Character @Nullable[] border, List<ColumnData<T>> columns) {
        @SuppressWarnings("unchecked")
        final ColumnData<T>[] rawColumns = columns.toArray(new ColumnData[0]);

        return Collector.of(
                () -> new RowAccumulator<>(rawColumns, border),
                RowAccumulator::add,
                RowAccumulator::combine,
                RowAccumulator::finish
        );
    }

    /**
     * Returns a Collector that maps each stream element once to a String[] row (using the provided
     * {@link ColumnData} getters), accumulates the rows and then renders the table.
     *
     * Example:
     * <pre>{@code
     * String table = people.stream().collect(AsciiTableCollector.toAsciiTable(columns));
     * }</pre>
     *
     * @param columns column definitions and getters used to extract cell values from stream elements
     * @param <T> element type of the stream
     * @return Collector producing the rendered table string
     */
    public static <T extends @Nullable Object> Collector<T, ?, String> toAsciiTable(List<ColumnData<T>> columns) {
        Objects.requireNonNull(columns, "columns cannot be null");
        return createCollector(null, columns);
    }

    /**
     * Returns a Collector that maps each stream element once to a String[] row (using the provided
     * {@link ColumnData} getters), accumulates the rows and then renders the table using the provided border.
     *
     * Example:
     * <pre>{@code
     * String table = people.stream().collect(AsciiTableCollector
     *                      .toAsciiTable(AsciiTable.FANCY_ASCII, columns));
     * }</pre>
     *
     * @param border border character array (see {@link AsciiTable} constants)
     * @param columns column definitions and getters used to extract cell values from stream elements
     * @param <T> element type of the stream
     * @return Collector producing the rendered table string
     */
    public static <T extends @Nullable Object> Collector<T, ?, String> toAsciiTable(@Nullable Character[] border, List<ColumnData<T>> columns) {
        Objects.requireNonNull(columns, "columns cannot be null");
        Objects.requireNonNull(border, "border cannot be null");
        return createCollector(border, columns);
    }

    /**
     * Convenience varargs overload accepting {@link ColumnData} elements.
     *
     * @param columns column data varargs
     * @param <T> element type of the stream
     * @return Collector producing the rendered table string
     */
    @SafeVarargs
    public static <T extends @Nullable Object> Collector<T, ?, String> toAsciiTable(ColumnData<T>... columns) {
        Objects.requireNonNull(columns, "columns cannot be null");
        return toAsciiTable(Arrays.asList(columns));
    }

    /**
     * Convenience varargs overload accepting a border and {@link ColumnData} elements.
     *
     * @param border border character array
     * @param columns column data varargs
     * @param <T> element type of the stream
     * @return Collector producing the rendered table string
     */
    @SafeVarargs
    public static <T extends @Nullable Object> Collector<T, ?, String> toAsciiTable(@Nullable Character[] border, ColumnData<T>... columns) {
        Objects.requireNonNull(border, "border cannot be null");
        Objects.requireNonNull(columns, "columns cannot be null");
        return toAsciiTable(border, Arrays.asList(columns));
    }
}
