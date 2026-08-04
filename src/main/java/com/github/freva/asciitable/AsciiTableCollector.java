package com.github.freva.asciitable;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
     * Returns a Collector that maps each stream element once to a String[] row (using the provided
     * {@link ColumnData} getters), accumulates the rows and then renders the table.
     *
     * Example:
     * <pre>{@code
     * String table = people.stream().collect(AsciiTableCollectors.toAsciiTable(columns));
     * }</pre>
     *
     * @param columns column definitions and getters used to extract cell values from stream elements
     * @param <T> element type of the stream
     * @return Collector producing the rendered table string
     */
    public static <T extends @Nullable Object> Collector<T, ?, String> toAsciiTable(List<ColumnData<T>> columns) {
        Objects.requireNonNull(columns, "columns cannot be null");
        final Column[] rawColumns = columns.toArray(new Column[0]);
        final int cols = columns.size();

        /**
         * Accumulates mapped row data during collection.
         *
         * <p>This class stores the mapped rows (each row is a {@code String[]} of length {@code cols})
         * in an {@link ArrayList}. It is intentionally simple:
         * - add(T) maps an item to a row and appends it to the internal list;
         * - combine(other) appends the other's rows to this accumulator to preserve encounter order.
         *
         * <p>Threading / parallel-safety:
         * <ul>
         *   <li>Instances of this accumulator are not themselves thread-safe. The {@link java.util.stream.Stream}
         *       framework guarantees that each accumulator instance is only used by a single thread during the
         *       accumulation phase (so you do not need additional synchronization inside the collector).</li>
         *   <li>If you use parallel streams, the collector's combiner merges partial accumulators by appending
         *       rows from one accumulator after another. For ordered streams, the stream implementation will
         *       combine partial results in a way that preserves the encounter order in the final result.
         *       If you require a strict, easily reasoned ordering with parallel execution, you can:
         *       <ul>
         *         <li>use a sequential stream (call {@code stream.sequential()}), or</li>
         *         <li>ensure your source is ordered and accept the stream framework's ordering guarantees.</li>
         *       </ul>
         *   </li>
         * </ul>
         *
         * <p>Memory: all rows are buffered (String[][]) because AsciiTable must compute column widths
         * before rendering.
         */
        final class RowAccumulator {
            final List<String[]> rows = new ArrayList<>();

            void add(T item) {
                String[] row = new String[cols];
                for (int i = 0; i < cols; i++) {
                    row[i] = columns.get(i).getCellValue(item);
                }
                rows.add(row);
            }

            RowAccumulator combine(RowAccumulator other) {
                rows.addAll(other.rows);
                return this;
            }

            String finish() {
                String[][] data = rows.toArray(new String[rows.size()][]);
                return AsciiTable.getTable(rawColumns, data);
            }
        }

        return Collector.of(
                RowAccumulator::new,
                RowAccumulator::add,
                (a, b) -> a.combine(b),
                RowAccumulator::finish
        );
    }

    /**
     * Returns a Collector that maps each stream element once to a String[] row (using the provided
     * {@link ColumnData} getters), accumulates the rows and then renders the table using the provided border.
     *
     * Example:
     * <pre>{@code
     * String table = people.stream().collect(AsciiTableCollectors
     *                      .toAsciiTable(AsciiTable.FANCY_ASCII, columns));
     * }</pre>
     *
     * @param border border character array (see {@link AsciiTable} constants)
     * @param columns column definitions and getters used to extract cell values from stream elements
     * @param <T> element type of the stream
     * @return Collector producing the rendered table string
     */
    public static <T extends @Nullable Object> Collector<T, ?, String> toAsciiTable(@Nullable Character[] border, List<ColumnData<T>> columns) {
        Objects.requireNonNull(border, "border cannot be null");
        Objects.requireNonNull(columns, "columns cannot be null");
        final Column[] rawColumns = columns.toArray(new Column[0]);
        final int cols = columns.size();

        /**
         * Accumulates mapped row data during collection (border-aware finisher).
         *
         * See the documentation in the other overload's RowAccumulator for threading and memory notes.
         */
        class RowAccumulator {
            final List<String[]> rows = new ArrayList<>();

            void add(T item) {
                String[] row = new String[cols];
                for (int i = 0; i < cols; i++) {
                    row[i] = columns.get(i).getCellValue(item);
                }
                rows.add(row);
            }

            RowAccumulator combine(RowAccumulator other) {
                rows.addAll(other.rows);
                return this;
            }

            String finish() {
                String[][] data = rows.toArray(new String[rows.size()][]);
                return AsciiTable.getTable(border, rawColumns, data);
            }
        }

        return Collector.of(
                RowAccumulator::new,
                RowAccumulator::add,
                (a, b) -> a.combine(b),
                RowAccumulator::finish
        );
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
        return toAsciiTable(List.of(columns));
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
        return toAsciiTable(border, List.of(columns));
    }
}