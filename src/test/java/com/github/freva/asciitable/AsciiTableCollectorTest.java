package com.github.freva.asciitable;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    public void collectsToTable_usingColumnDataList() {
        List<Person> people = samplePeople();

        List<ColumnData<Person>> columns = Arrays.asList(
                new Column().with(p -> Integer.toString(p.id)),
                new Column().header("Name").with(p -> p.name),
                new Column().header("Age").dataAlign(HorizontalAlign.RIGHT).with(p -> Integer.toString(p.age))
        );

        String expected = AsciiTable.getTable(people, columns);
        String actual = people.stream().collect(AsciiTableCollector.toAsciiTable(columns));

        assertEquals(expected, actual);
    }

    @Test
    public void collectsToTable_usingBorderOverload() {
        List<Person> people = samplePeople();

        List<ColumnData<Person>> columns = Arrays.asList(
                new Column().with(p -> Integer.toString(p.id)),
                new Column().header("Name").with(p -> p.name),
                new Column().header("Age").dataAlign(HorizontalAlign.RIGHT).with(p -> Integer.toString(p.age))
        );

        Character[] border = AsciiTable.BASIC_ASCII;
        String expected = AsciiTable.getTable(border, people, columns);
        String actual = people.stream().collect(AsciiTableCollector.toAsciiTable(border, columns));

        assertEquals(expected, actual);
    }

    @Test
    public void collectsToTable_usingVarargsColumns() {
        List<Person> people = samplePeople();

        ColumnData<Person> c1 = new Column().with(p -> Integer.toString(p.id));
        ColumnData<Person> c2 = new Column().header("Name").with(p -> p.name);
        ColumnData<Person> c3 = new Column().header("Age").dataAlign(HorizontalAlign.RIGHT).with(p -> Integer.toString(p.age));

        String expected = AsciiTable.getTable(people, Arrays.asList(c1, c2, c3));
        String actual = people.stream().collect(AsciiTableCollector.toAsciiTable(c1, c2, c3));

        assertEquals(expected, actual);
    }

    @Test
    public void collectsToTable_usingBorderVarargs() {
        List<Person> people = samplePeople();

        ColumnData<Person> c1 = new Column().with(p -> Integer.toString(p.id));
        ColumnData<Person> c2 = new Column().header("Name").with(p -> p.name);
        ColumnData<Person> c3 = new Column().header("Age").dataAlign(HorizontalAlign.RIGHT).with(p -> Integer.toString(p.age));

        Character[] border = AsciiTable.BASIC_ASCII;

        String expected = AsciiTable.getTable(border, people, Arrays.asList(c1, c2, c3));
        String actual = people.stream().collect(AsciiTableCollector.toAsciiTable(border, c1, c2, c3));

        assertEquals(expected, actual);
    }

    @Test
    public void parallelCollect_matchesSequential_and_getTable() {
        // build an ordered source with unique ids to make testing deterministic
        List<Person> people = IntStream.range(0, 2000) // larger N to encourage splitting
                .mapToObj(i -> new Person(i, "Name" + i, 20 + (i % 50)))
                .collect(Collectors.toList());

        List<ColumnData<Person>> columns = List.of(
                new Column().with(p -> Integer.toString(p.id)),
                new Column().header("Name").with(p -> p.name),
                new Column().header("Age").dataAlign(HorizontalAlign.RIGHT).with(p -> Integer.toString(p.age))
        );

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
}