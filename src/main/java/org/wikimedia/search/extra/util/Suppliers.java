package org.wikimedia.search.extra.util;

import java.util.function.Supplier;

public class Suppliers {
    private Suppliers() {
    }

    public static class MutableSupplier<T> implements Supplier<T> {
       T obj;

       @Override
       public T get() {
           return obj;
       }

       public void set(T obj) {
           this.obj = obj;
       }
    }
}
