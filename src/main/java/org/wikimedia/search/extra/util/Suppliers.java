package org.wikimedia.search.extra.util;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class Suppliers {
    private Suppliers() {
    }

    public static class MutableSupplier<T> implements Supplier<T> {
       @Nullable T obj;

       @Override
       @Nullable
       public T get() {
           return obj;
       }

       public void set(T obj) {
           this.obj = obj;
       }
    }
}
