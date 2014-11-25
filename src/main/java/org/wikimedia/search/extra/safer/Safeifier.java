package org.wikimedia.search.extra.safer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.search.Query;

/**
 * Single use class to make a query safe. External users should build it with a
 * config and then call safeify once. Internally it recursively calls safeify as
 * it analyzes the query.
 */
public class Safeifier {
    /**
     * Module containing actions to take to safeify queries of certain types.
     */
    public interface ActionModule {
        void register(Safeifier safeifier);
    }

    public interface Action<I extends Query, O extends Query> {
        O apply(Safeifier safeifier, I q);
    }

    private final Map<Class<?>, Action<Query, Query>> registry = new HashMap<>();
    private final boolean errorOnUnknownQueryType;

    public Safeifier(boolean errorOnUnknownQueryType, Iterable<ActionModule> modules) {
        this.errorOnUnknownQueryType = errorOnUnknownQueryType;
        DefaultNoopSafeifierActions.register(this);
        DefaultQueryExplodingSafeifierActions.register(this);
        for (ActionModule module: modules) {
            module.register(this);
        }
    }

    public Safeifier(boolean errorOnUnknownQueryType, ActionModule... modules) {
        this(errorOnUnknownQueryType, Arrays.asList(modules));
    }

    public Safeifier(ActionModule... modules) {
        this(true, modules);
    }

    @SuppressWarnings("unchecked")
    public void register(Class<? extends Query> queryClass, Action<? extends Query, ? extends Query> handler) {
        registry.put(queryClass, (Action<Query, Query>) handler);
    }

    public Query safeify(Query q) {
        Action<Query, Query> action = registry.get(q.getClass());
        if (action == null) {
            if (errorOnUnknownQueryType) {
                throw new UnknownQueryException(q);
            } else {
                return q;
            }
        }
        return action.apply(this, q);
    }
}
