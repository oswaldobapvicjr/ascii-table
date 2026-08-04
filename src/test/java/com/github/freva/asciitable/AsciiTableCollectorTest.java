package com.github.freva.asciitable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

public class AsciiTableCollectorTest {

    static class Person {
        final int id;
        final String name;
        final int age;

        Person(int id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }
    }

    private List<Person> samplePeople() {
        return Arrays.asList(
                new Person(1, "Alice", 30),
                new Person(2, "Bob", 25),
                new Person(3, "Carol", 42)
        );
    }
    
    private List<ColumnData<Person>> columns() {
        return List.of(
            new Column().with(p -> Integer.toString(p.id)),
            new Column().header("Name").with(p -> p.name),
            new Column().header("Age").dataAlign(HorizontalAlign.RIGHT).with(p -> Integer.toString(p.age))
        );
    }

    @SuppressWarnings("unchecked")
    private Collector<Person, Object, String> collector() {
        return (Collector<Person, Object, String>) AsciiTableCollector.toAsciiTable(columns());
    }

    @Test
    public void collectsToTable_usingColumnDataList() {
        List<Person> people = samplePeople();
        List<ColumnData<Person>> columns = columns();

        String expected = AsciiTable.getTable(people, columns);
        String actual = people.stream().collect(AsciiTableCollector.toAsciiTable(columns));

        assertEquals(expected, actual);
    }

    @Test
    public void collectsToTable_usingBorderOverload() {
        List<Person> people = samplePeople();
        List<ColumnData<Person>> columns = columns();

        Character[] border = AsciiTable.BASIC_ASCII;
        String expected = AsciiTable.getTable(border, people, columns);
        String actual = people.stream().collect(AsciiTableCollector.toAsciiTable(border, columns));

        assertEquals(expected, actual);
    }

    @Test
    public void collectsToTable_usingVarargsColumns() {
        List<Person> people = samplePeople();
        List<ColumnData<Person>> columns = columns();

        String expected = AsciiTable.getTable(people, columns);
        String actual = people.stream()
                .collect(AsciiTableCollector.toAsciiTable(columns.get(0), columns.get(1), columns.get(2)));

        assertEquals(expected, actual);
    }

    @Test
    public void collectsToTable_usingBorderVarargs() {
        List<Person> people = samplePeople();
        List<ColumnData<Person>> columns = columns();

        Character[] border = AsciiTable.BASIC_ASCII;

        String expected = AsciiTable.getTable(border, people, columns);
        String actual = people.stream()
                .collect(AsciiTableCollector.toAsciiTable(border, columns.get(0), columns.get(1), columns.get(2)));

        assertEquals(expected, actual);
    }

    @Test
    public void parallelCollect_matchesSequential_and_getTable() {
        // build an ordered source with unique ids to make testing deterministic
        List<Person> people = IntStream.range(0, 2000) // larger N to encourage splitting
                .mapToObj(i -> new Person(i, "Name" + i, 20 + (i % 50)))
                .collect(Collectors.toList());

        List<ColumnData<Person>> columns = columns();

        // expected using existing collection-based API (ordered)
        String expected = AsciiTable.getTable(people, columns);

        // actual using the optimized collector on a parallel stream
        String actualParallel = people.parallelStream().collect(AsciiTableCollector.toAsciiTable(columns));
        assertEquals(expected, actualParallel);

        // also ensure parallel border overload matches
        Character[] border = AsciiTable.BASIC_ASCII;
        String expectedBorder = AsciiTable.getTable(border, people, columns);
        String actualParallelBorder = people.parallelStream().collect(AsciiTableCollector.toAsciiTable(border, columns));
        assertEquals(expectedBorder, actualParallelBorder);
    }
    
    @Test
    public void combine_sameInstance_returnsSame() {
        Collector<Person, Object, String> c = collector();
        Supplier<Object> supplier = c.supplier();
        BiConsumer<Object, Person> accumulator = c.accumulator();
        BinaryOperator<Object> combiner = c.combiner();

        Object acc = supplier.get();
        // add one element to ensure rows is non-empty
        accumulator.accept(acc, new Person(1, "A", 30));

        // calling combiner with the same instance should return the same instance (early return)
        Object result = combiner.apply(acc, acc);
        assertSame(acc, result, "Combiner should return 'this' when other == this");
    }

    @Test
    public void combine_thisEmpty_returnsOther() {
        Collector<Person, Object, String> c = collector();
        Supplier<Object> supplier = c.supplier();
        BiConsumer<Object, Person> accumulator = c.accumulator();
        BinaryOperator<Object> combiner = c.combiner();

        Object emptyAcc = supplier.get();
        Object nonEmptyAcc = supplier.get();
        accumulator.accept(nonEmptyAcc, new Person(2, "B", 25));

        Object result = combiner.apply(emptyAcc, nonEmptyAcc);
        assertSame(nonEmptyAcc, result, "Combiner should return other when this.rows is empty");
    }

    @Test
    public void combine_otherEmpty_returnsThis() {
        Collector<Person, Object, String> c = collector();
        Supplier<Object> supplier = c.supplier();
        BiConsumer<Object, Person> accumulator = c.accumulator();
        BinaryOperator<Object> combiner = c.combiner();

        Object nonEmptyAcc = supplier.get();
        Object emptyAcc = supplier.get();
        accumulator.accept(nonEmptyAcc, new Person(3, "C", 40));

        Object result = combiner.apply(nonEmptyAcc, emptyAcc);
        assertSame(nonEmptyAcc, result, "Combiner should return this when other.rows is empty");
    }

    @Test
    public void combine_bothNonEmpty_mergesAndPreservesOrder() {
        Collector<Person, Object, String> c = collector();
        Supplier<Object> supplier = c.supplier();
        BiConsumer<Object, Person> accumulator = c.accumulator();
        BinaryOperator<Object> combiner = c.combiner();
        Function<Object, String> finisher = c.finisher();

        // build two partial accumulators that each receive some rows
        Object left = supplier.get();
        Object right = supplier.get();

        Person a1 = new Person(10, "L1", 30);
        Person a2 = new Person(11, "L2", 31);
        Person b1 = new Person(20, "R1", 40);

        // left: a1, a2
        accumulator.accept(left, a1);
        accumulator.accept(left, a2);

        // right: b1
        accumulator.accept(right, b1);

        // combine left + right -> should append right rows after left rows
        Object combined = combiner.apply(left, right);

        String combinedTable = finisher.apply(combined);

        // expected table built using sequential API for exact ordering and formatting parity
        String expected = AsciiTable.getTable(List.of(a1, a2, b1), columns());

        assertEquals(expected, combinedTable, "Combined finisher output should match expected table for appended rows");
    }
}
